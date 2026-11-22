import { createRoot } from "react-dom/client";
import "@fontsource-variable/atkinson-hyperlegible-next/wght.css";
import "@fontsource-variable/atkinson-hyperlegible-mono/wght.css";
import "./index.css";
import { App } from "./App";

createRoot(document.getElementById("root")!).render(<App />);
