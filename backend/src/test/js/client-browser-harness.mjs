import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import { resolve, sep } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { fileURLToPath } from "node:url";

export const browser = [
  process.env.CHROME_BIN,
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
  "/usr/bin/google-chrome", "/usr/bin/chromium", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
].find((path) => path && existsSync(path));
const staticRoot = fileURLToPath(new URL("../../main/resources/static/", import.meta.url));
const types = { html: "text/html", css: "text/css", js: "text/javascript", woff2: "font/woff2" };

async function serveStatic(request, response) {
  const path = new URL(request.url, "http://localhost").pathname;
  const file = resolve(staticRoot, "." + path);
  if (!file.startsWith(resolve(staticRoot) + sep)) {
    response.writeHead(404).end();
    return;
  }
  try {
    const body = await readFile(file);
    response.writeHead(200, { "Content-Type": types[file.split(".").at(-1)] ?? "text/plain" }).end(body);
  } catch {
    response.writeHead(404).end();
  }
}

export async function until(condition, label) {
  for (let i = 0; i < 120; i++) {
    try {
      const value = await condition();
      if (value) {
        return value;
      }
    } catch (error) {
      if (!/context|ENOENT|fetch failed/i.test(error.message)) {
        throw error;
      }
    }
    await delay(50);
  }
  assert.fail("Timed out waiting for " + label);
}

export async function startBrowser(fixture, onEvent = () => {}) {
  let nextId = 0;
  const waiting = new Map();
  let socket;
  function command(method, params = {}) {
    const id = ++nextId;
    return new Promise((resolveRequest, reject) => {
      waiting.set(id, { resolveRequest, reject });
      socket.send(JSON.stringify({ id, method, params }));
    });
  }
  async function evaluate(expression) {
    const reply = await command("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
    if (reply.exceptionDetails) {
      throw new Error(reply.exceptionDetails.exception?.description ?? reply.exceptionDetails.text);
    }
    return reply.result.value;
  }

  const server = createServer((request, response) => {
    Promise.resolve(fixture(request, response)).then(() => {
      if (!response.headersSent && !response.writableEnded && !response.destroyed) {
        return serveStatic(request, response);
      }
    }).catch(() => {
      if (!response.headersSent) response.writeHead(500);
      response.end();
    });
  });
  await new Promise((ready) => server.listen(0, "127.0.0.1", ready));
  const origin = "http://127.0.0.1:" + server.address().port;
  const profile = await mkdtemp(resolve(tmpdir(), "lms-client-browser-"));
  const chrome = spawn(browser, ["--headless=new", "--disable-background-networking", "--no-first-run",
    "--window-size=1440,900", "--remote-debugging-port=0", "--user-data-dir=" + profile, "about:blank"],
    { windowsHide: true, stdio: "ignore" });
  const endpoint = await until(async () => {
    const [port] = (await readFile(resolve(profile, "DevToolsActivePort"), "utf8")).split("\n");
    const pages = await (await fetch("http://127.0.0.1:" + port + "/json/list")).json();
    return pages.find((page) => page.type === "page")?.webSocketDebuggerUrl;
  }, "headless Chromium");
  socket = new WebSocket(endpoint);
  await new Promise((ready, fail) => {
    socket.addEventListener("open", ready, { once: true });
    socket.addEventListener("error", fail, { once: true });
  });
  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    onEvent(message);
    const request = waiting.get(message.id);
    if (request) {
      waiting.delete(message.id);
      if (message.error) request.reject(new Error(message.error.message));
      else request.resolveRequest(message.result);
    }
  });
  await command("Page.enable");
  await command("Runtime.enable");
  return { origin, command, evaluate, close };

  async function close() {
    if (chrome.exitCode === null) {
      await Promise.race([command("Browser.close").catch(() => {}), delay(500)]);
      chrome.kill();
    }
    socket.close();
    server.close();
    await rm(profile, { recursive: true, force: true, maxRetries: 8, retryDelay: 200 });
  }
}
