import { isOrderReference } from "./client-api.js";

const dateFormat = new Intl.DateTimeFormat("en-HK", {
  dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Hong_Kong"
});
const hkdFormat = new Intl.NumberFormat("en-HK", { style: "currency", currency: "HKD", currencyDisplay: "code" });
const orderStatuses = new Map([
  ["AWAITING_PAYMENT", ["Awaiting payment", "waiting"]],
  ["READY_FOR_PICKING", ["Ready for picking", "working"]],
  ["PICKING", ["Picking", "working"]],
  ["READY_FOR_PICKUP", ["Ready for pickup", "working"]],
  ["SHIPPED", ["Shipped", "working"]],
  ["OUT_FOR_DELIVERY", ["Out for delivery", "working"]],
  ["DELIVERY_FAILED", ["Delivery failed", "attention"]],
  ["READY_FOR_REDELIVERY", ["Ready for redelivery", "working"]],
  ["DELIVERED", ["Delivered", "done"]],
  ["CANCELLED", ["Cancelled", "neutral"]]
]);

export const byId = (id) => document.getElementById(id);
export const hkd = (amount) => hkdFormat.format(amount);

export function make(tag, text, className) {
  const element = document.createElement(tag);
  if (text !== undefined) element.textContent = text;
  if (className) element.className = className;
  return element;
}

export function timestamp(value) {
  const element = make("time", dateFormat.format(new Date(value)) + " HKT");
  element.dateTime = value;
  return element;
}

export function badge(status) {
  const [label, tone] = orderStatuses.get(status) ?? ["Status unavailable", "neutral"];
  const element = make("span", label, "client-order-badge");
  element.dataset.tone = tone;
  return element;
}

export function setNotice(node, message, state = "") {
  node.textContent = message;
  node.hidden = !message;
  node.dataset.state = state;
}

export function clientOrderDetailsUrl(reference) {
  if (!isOrderReference(reference)) return null;
  const query = new URLSearchParams({ reference });
  return "/client/order-details.html?" + query;
}

export function orderLoadMessage(error, subject) {
  if (error?.code === "CLIENT_RESPONSE_INVALID") return "The server returned incomplete " + subject + " data. Try refreshing.";
  return "Could not load " + subject + ". Check your connection and try again.";
}
