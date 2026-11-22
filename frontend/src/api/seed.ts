// The sample world: every account, product, order and log row the app shows at start.
// All of it is invented. It shows the shop at one moment, 2026-11-12 09:14, and agrees with itself:
// the counters, the dashboard and every list are counted from these rows, never typed in.

import type { Database, PickupList } from "./store";
import type {
  AuditLogRow,
  AuditTarget,
  ClientInstruction,
  DeliveryAttempt,
  DeliveryPackage,
  InstructionType,
  Order,
  OrderStatus,
  Payment,
  PickingScanRow,
  PickingTask,
  ProviderAnswer,
  Refund,
  RefundStatus,
  Role,
  SecurityLogRow,
  ShortageCase,
  User,
} from "./types";
import { formatMoney, parseTime, TIME } from "../lib/format";

const { MINUTE: MIN, HOUR, DAY } = TIME;
export const SAMPLE_PASSWORD = "Linen7Route";
const NOW = parseTime("2026-11-12 09:14")!;

function T(text: string): number {
  const value = parseTime(text);
  if (value === null) throw new Error(`Bad sample time: ${text}`);
  return value;
}

function makeRandom(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const rand = makeRandom(20261112);
const between = (low: number, high: number) => low + Math.floor(rand() * (high - low + 1));
const pick = <T,>(list: readonly T[]): T => list[Math.floor(rand() * list.length)];
const chance = (p: number) => rand() < p;

const AGENT = {
  chrome: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36",
  firefox: "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
  edge: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36 Edg/142.0.0.0",
  android: "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36",
  iphone: "Mozilla/5.0 (iPhone; CPU iPhone OS 19_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/19.1 Mobile/15E148 Safari/604.1",
  mac: "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/19.1 Safari/605.1.15",
};

// ---------------------------------------------------------------------------------------------
// Products: a homeware and stationery shop. [name, price, physical, reserved, low below, status]
// ---------------------------------------------------------------------------------------------

type ProductRow = [string, number, number, number, number, ("INACTIVE" | undefined)?];

const PRODUCTS: ProductRow[] = [
  ["Stoneware mug, 350 ml, oat", 80, 12, 2, 3],
  ["Gel pens 0.5 mm, black, pack of 3", 45, 5, 3, 5],
  ["Porcelain dinner set, 16 pieces", 1234.5, 40, 0, 10],
  ["Rattan desk tray", 15.9, 0, 0, 0, "INACTIVE"],
  ["Insulated bottle, 750 ml, moss", 230, 6, 8, 2],
  ["Stoneware mug, 350 ml, moss", 80, 18, 1, 3],
  ["Stoneware mug, 350 ml, slate", 80, 15, 0, 3],
  ["Stoneware mug, 350 ml, clay", 80, 9, 1, 3],
  ["Gel pens 0.5 mm, blue, pack of 3", 45, 31, 2, 5],
  ["Gel pens 0.5 mm, red, pack of 3", 45, 22, 0, 5],
  ["Dotted notebook, A5, 120 pages, sand", 68, 44, 3, 8],
  ["Dotted notebook, A5, 120 pages, evergreen", 68, 37, 2, 8],
  ["Dotted notebook, A5, 120 pages, charcoal", 68, 29, 0, 8],
  ["Insulated bottle, 750 ml, sky", 230, 14, 2, 2],
  ["Insulated bottle, 750 ml, rust", 230, 11, 0, 2],
  ["Canvas tote bag, natural", 95, 52, 4, 10],
  ["Soy candle, cedar, 200 g", 118, 9, 5, 6],
  ["Soy candle, fig, 200 g", 118, 16, 1, 6],
  ["Soy candle, linen, 200 g", 118, 20, 0, 6],
  ["Canvas tote bag, black", 95, 38, 2, 10],
  ["Linen tea towel, oat", 72, 26, 0, 6],
  ["Linen tea towel, stripe", 72, 19, 1, 6],
  ["Porcelain side plate, 20 cm", 58, 64, 6, 12],
  ["Porcelain dinner plate, 27 cm", 82, 48, 4, 12],
  ["Porcelain cereal bowl, 15 cm", 64, 55, 2, 12],
  ["Glass tumblers, 300 ml, set of 4", 168, 21, 1, 4],
  ["Oak desk organiser", 345, 8, 0, 2],
  ["Weekly planner 2027, A5", 128, 120, 9, 20],
  ["Fineliner set, 12 colours", 96, 33, 2, 6],
  ["Mechanical pencil, 0.7 mm", 38, 60, 3, 10],
  ["Erasers, pack of 2", 12.5, 140, 4, 20],
  ["Washi tape, set of 5", 54, 47, 2, 8],
  ["Kraft envelopes C5, pack of 25", 42, 75, 0, 10],
  ["LED desk lamp, warm white", 489, 7, 1, 2],
  ["Cork coasters, set of 6", 48, 36, 0, 6],
  ["Ceramic planter, 12 cm, white", 88, 23, 1, 4],
  ["Ceramic planter, 18 cm, white", 138, 12, 0, 3],
  ["Cotton cushion cover, 45 cm, oat", 110, 18, 0, 4],
  ["Cotton cushion cover, 45 cm, moss", 110, 15, 2, 4],
  ["Wool throw, 130 x 170 cm", 520, 6, 0, 2],
  ["Bamboo cutting board, large", 158, 19, 1, 4],
  ["Digital kitchen scale", 199, 13, 0, 3],
  ["Pour-over coffee dripper", 145, 17, 2, 4],
  ["Loose-leaf tea tin, 100 g", 76, 41, 0, 8],
  ["Stainless lunch box, 1 L", 168, 22, 1, 4],
  ["Reusable straw set, steel", 39, 58, 0, 10],
  ["Recycled copy paper A4, 500 sheets", 49, 90, 6, 20],
  ["Sticky notes 76 x 76 mm, 6 pads", 32, 84, 3, 12],
  ["Highlighters, 4 colours", 36, 67, 2, 10],
  ["Ballpoint pens, blue, pack of 10", 28, 110, 5, 15],
  ["Fountain pen, fine nib", 265, 9, 0, 2],
  ["Ink cartridges, black, pack of 6", 34, 48, 1, 8],
  ["Sketchbook, A4, 80 pages", 88, 27, 0, 6],
  ["Watercolour set, 24 pans", 210, 11, 1, 3],
  ["Cast iron pan, 26 cm", 398, 8, 0, 2],
  ["Chef's knife, 20 cm", 450, 7, 1, 2],
  ["Linen napkins, set of 4", 135, 16, 0, 4],
  ["Enamel mug, 300 ml", 65, 34, 2, 6],
  ["French press, 1 L", 238, 10, 0, 3],
  ["Glass storage jars, set of 3", 156, 18, 1, 4],
  ["Wooden spoon set, 3 pieces", 78, 29, 0, 6],
  ["Oven mitts, pair", 92, 21, 1, 4],
  ["Hand soap refill, 500 ml", 58, 46, 2, 10],
  ["Dish brush, beech", 35, 52, 0, 10],
  ["Laundry basket, seagrass", 268, 6, 0, 2, "INACTIVE"],
  ["Wall clock, 30 cm", 310, 9, 1, 2],
  ["Photo frame, A4, oak", 125, 24, 0, 5],
  ["Bookends, steel, pair", 98, 17, 0, 4],
  ["Lined notebooks, A6, pack of 3", 52, 63, 2, 10],
  ["Letter tray, 2 tiers", 118, 14, 0, 3],
  ["Stapler, full strip", 86, 20, 1, 4],
  ["Staples 26/6, box of 5000", 18, 95, 0, 15],
  ["Paper clips, box of 100", 9.9, 130, 2, 20],
  ["Scissors, 21 cm", 45, 39, 0, 8],
  ["Steel ruler, 30 cm", 26, 57, 1, 10],
  ["Pencil case, canvas", 58, 31, 0, 6],
  ["Index cards, pack of 100", 22, 72, 0, 12],
  ["Glue stick, 21 g", 14.5, 88, 3, 15],
  ["Solar calculator", 98, 16, 0, 4],
  ["Whiteboard, 60 x 45 cm", 238, 7, 0, 2],
  ["Whiteboard markers, 4 colours", 42, 44, 2, 8],
  ["Monitor stand, bamboo", 288, 9, 1, 2],
  ["Laptop sleeve, 14 inch, felt", 198, 13, 0, 3],
  ["Cable organisers, set of 6", 44, 41, 0, 8],
  ["Felt desk mat, 80 x 30 cm", 168, 15, 1, 3],
  ["Phone stand, oak", 88, 22, 0, 5],
  ["Ceramic vase, 20 cm", 165, 12, 0, 3],
  ["Scented sachets, cedar, set of 3", 48, 26, 0, 6],
  ["Bath towel, cotton, oat", 168, 20, 1, 4],
  ["Hand towel, cotton, oat", 88, 28, 0, 6],
  ["Bath mat, cotton", 138, 14, 0, 3],
  ["Linen shower curtain", 258, 8, 0, 2],
  ["Toothbrush holder, stoneware", 68, 19, 0, 4],
  ["Soap dish, stoneware", 48, 23, 1, 4],
  ["Kraft storage boxes, set of 3", 108, 25, 0, 5],
  ["Kraft gift wrap, 5 m", 38, 46, 2, 8],
  ["Greeting cards, set of 8", 64, 33, 0, 6],
  ["Jute twine, 50 m", 28, 51, 0, 8],
  ["Dinner plates, 27 cm, set of 4", 298, 11, 0, 3],
  ["Acacia salad bowl", 228, 9, 0, 2],
  ["Oak serving tray", 248, 8, 1, 2],
  ["Teapot, 800 ml", 268, 10, 0, 2],
  ["Milk jug, 250 ml", 78, 18, 0, 4],
  ["Sugar bowl, stoneware", 72, 15, 0, 3],
  ["Espresso cups, set of 2", 98, 20, 1, 4],
  ["Wine glasses, set of 2", 158, 14, 0, 3],
  ["Water carafe, 1 L", 128, 16, 0, 3],
  ["Silicone ice cube tray", 42, 35, 0, 6],
  ["Canvas apron", 148, 12, 1, 3],
  ["Picnic blanket", 288, 7, 0, 2],
  ["Insulated food jar, 500 ml", 188, 17, 0, 4],
  ["Thermos flask, 500 ml", 178, 19, 1, 4],
  ["Floor cushion, cotton", 238, 6, 0, 2, "INACTIVE"],
  ["Coir door mat", 158, 10, 0, 2],
  ["Umbrella stand, steel", 198, 5, 0, 1],
  ["Oak coat hooks, set of 3", 118, 13, 0, 3],
  ["Shelf brackets, pair", 68, 24, 0, 5],
  ["Floating shelf, 60 cm", 228, 9, 0, 2],
  ["Magazine rack", 168, 8, 0, 2],
  ["Waste bin, 10 L", 138, 12, 1, 3],
  ["Desk calendar 2027", 78, 64, 3, 12],
  ["Wall planner 2027", 98, 42, 1, 8],
  ["Round labels, roll of 500", 24, 70, 0, 10],
  ["Label maker tape, 12 mm", 58, 36, 1, 6],
  ["Kraft shipping boxes, pack of 10", 85, 48, 0, 10],
  ["Bubble wrap roll, 5 m", 46, 39, 0, 8],
  ["Packing tape, 3 rolls", 39, 57, 2, 10],
  ["Tape dispenser", 72, 18, 0, 4],
];

// ---------------------------------------------------------------------------------------------
// Accounts
// ---------------------------------------------------------------------------------------------

interface UserSeed {
  username: string;
  role: Role;
  created: string;
  status?: User["accountStatus"];
  lastLogin?: string | null;
  failed?: number;
  lockUntil?: string;
  temporary?: { password: string; issued: string };
  profile?: User["deliveryWorkerProfile"];
}

const STAFF: UserSeed[] = [
  { username: "admin_main", role: "ADMIN", created: "2026-08-01 08:00", lastLogin: "2026-11-12 09:14" },
  { username: "admin_second", role: "ADMIN", created: "2026-08-03 10:20", lastLogin: "2026-11-11 16:40" },
  { username: "wh_old_account", role: "WAREHOUSE_WORKER", created: "2026-08-02 09:00", status: "LOCKED", lastLogin: "2026-10-30 10:12" },
  { username: "wh_picker_01", role: "WAREHOUSE_WORKER", created: "2026-08-05 09:30", lastLogin: "2026-11-11 17:02", failed: 5, lockUntil: "2026-11-12 09:26" },
  { username: "wh_picker_02", role: "WAREHOUSE_WORKER", created: "2026-08-05 09:35", lastLogin: "2026-11-12 07:25" },
  {
    username: "dl_driver_01",
    role: "DELIVERY_WORKER",
    created: "2026-08-06 10:00",
    lastLogin: "2026-11-12 08:05",
    profile: { employeeNumber: "E-100", vehicleType: "Van", vehiclePlate: "AB 1234", equipmentNote: "Trolley" },
  },
  {
    username: "dl_driver_02",
    role: "DELIVERY_WORKER",
    created: "2026-08-06 10:05",
    status: "BANNED",
    lastLogin: "2026-11-12 07:20",
    profile: { employeeNumber: "E-101", vehicleType: "Van", vehiclePlate: "CD 5678", equipmentNote: "" },
  },
  { username: "wh_picker_04", role: "WAREHOUSE_WORKER", created: "2026-08-20 09:00", lastLogin: "2026-11-12 06:58" },
  { username: "wh_picker_05", role: "WAREHOUSE_WORKER", created: "2026-09-15 09:00", status: "INACTIVE", lastLogin: "2026-11-11 16:15" },
  {
    username: "dl_driver_03",
    role: "DELIVERY_WORKER",
    created: "2026-09-20 11:00",
    lastLogin: "2026-11-12 07:55",
    profile: { employeeNumber: "E-103", vehicleType: "Electric cargo bike", vehiclePlate: "", equipmentNote: "Two insulated boxes" },
  },
  {
    username: "dl_driver_04",
    role: "DELIVERY_WORKER",
    created: "2026-10-02 10:30",
    lastLogin: "2026-11-12 08:10",
    profile: { employeeNumber: "E-104", vehicleType: "Van", vehiclePlate: "EF 2468", equipmentNote: "Hand truck" },
  },
  {
    username: "wh_picker_03",
    role: "WAREHOUSE_WORKER",
    created: "2026-11-12 08:40",
    lastLogin: null,
    temporary: { password: "Kd7p-Wm3x-Qh9t", issued: "2026-11-12 08:40" },
  },
];

const CLIENT_NUMBERS = Array.from({ length: 30 }, (_, i) => 450 + i);

function clientSeed(number: number, index: number): UserSeed {
  const username = `client_0${number}`;
  const createdDay = 1 + Math.floor((index * 80) / 30);
  const created = new Date(T("2026-08-01 12:00") + createdDay * DAY + between(0, 9) * HOUR);
  const pad = (n: number) => String(n).padStart(2, "0");
  const createdText = `${created.getFullYear()}-${pad(created.getMonth() + 1)}-${pad(created.getDate())} ${pad(created.getHours())}:${pad(created.getMinutes())}`;
  const seed: UserSeed = { username, role: "CLIENT", created: createdText };
  if (username === "client_0457") seed.status = "INACTIVE";
  if (username === "client_0470") seed.status = "BANNED";
  if (username === "client_0461") seed.failed = 2;
  if (username === "client_0474") seed.failed = 1;
  if (username === "client_0476") seed.temporary = { password: "Rb4t-Lc8n-Vw2p", issued: "2026-11-11 15:30" };
  return seed;
}

// ---------------------------------------------------------------------------------------------
// The world builder
// ---------------------------------------------------------------------------------------------

export function createSampleWorld(): Database {
  const db: Database = {
    users: [],
    products: [],
    orders: [],
    orderItems: [],
    payments: [],
    refunds: [],
    pickingTasks: [],
    pickingAssignments: [],
    packages: [],
    attempts: [],
    instructions: [],
    shortageCases: [],
    events: [],
    auditLog: [],
    securityLog: [],
    pickingScans: [],
    pickupLists: [],
    logCleanup: null,
    session: null,
    exitNotice: null,
    testModeIsEnabled: true,
    nextID: {},
  };

  // ----- products
  PRODUCTS.forEach(([name, price, physical, reserved, low, status], index) => {
    db.products.push({
      productID: index + 1,
      SKU: `SKU-${1001 + index}`,
      name,
      currentPrice: price,
      physicalStock: physical,
      reservedStock: reserved,
      lowStockThreshold: low,
      listingStatus: status ?? "ACTIVE",
      createdTime: index < 120 ? T("2026-08-02 10:30") : T("2026-09-10 14:00") + (index - 120) * 6 * DAY,
      isTest: false,
    });
  });
  const productBySKU = new Map(db.products.map((p) => [p.SKU, p]));
  const sku = (code: string) => {
    const product = productBySKU.get(code);
    if (!product) throw new Error(`No product ${code}`);
    return product;
  };

  // ----- accounts
  const seeds = [...STAFF, ...CLIENT_NUMBERS.map(clientSeed)].sort((a, b) => T(a.created) - T(b.created));
  seeds.forEach((s, index) => {
    db.users.push({
      userID: index + 1,
      username: s.username,
      role: s.role,
      accountStatus: s.status ?? "ACTIVE",
      createdTime: T(s.created),
      lastLoginTime: s.lastLogin ? T(s.lastLogin) : null,
      failedLoginCount: s.failed ?? 0,
      lockUntil: s.lockUntil ? T(s.lockUntil) : null,
      password: SAMPLE_PASSWORD,
      temporaryPassword: s.temporary?.password ?? null,
      temporaryPasswordExpiryTime: s.temporary ? T(s.temporary.issued) + DAY : null,
      mustChangePassword: Boolean(s.temporary),
      isTestAccount: false,
      deliveryWorkerProfile: s.role === "DELIVERY_WORKER" ? (s.profile ?? null) : null,
    });
  });
  const userByName = new Map(db.users.map((u) => [u.username, u]));
  const user = (name: string) => {
    const found = userByName.get(name);
    if (!found) throw new Error(`No user ${name}`);
    return found;
  };
  const clients = db.users.filter((u) => u.role === "CLIENT");

  // ----- order specs (built first, numbered by creation time, filled afterwards)
  interface Spec {
    key: string;
    created: number;
    client: string;
    build: (o: Order) => void;
  }
  const specs: Spec[] = [];
  const specOrder = new Map<string, Order>();

  // Row helpers --------------------------------------------------------------------------------
  let eventID = 1;
  let itemID = 1;
  let paymentID = 1;
  let taskID = 1;
  let assignmentID = 1;
  let packageID = 1;
  let attemptID = 1;
  let instructionTemp = 1;

  const event = (o: Order, time: number, text: string, actor: string | null = null) => {
    db.events.push({ eventID: eventID++, orderID: o.orderID, time, text, actor });
  };

  const items = (o: Order, list: Array<[string, number, number?]>) => {
    let total = 0;
    for (const [code, quantity, picked] of list) {
      const product = sku(code);
      db.orderItems.push({
        orderItemID: itemID++,
        orderID: o.orderID,
        productID: product.productID,
        quantity,
        pickedQuantity: picked ?? 0,
        unitPrice: product.currentPrice,
      });
      total += product.currentPrice * quantity;
    }
    o.totalAmount = Math.round(total * 100) / 100;
  };

  const setPicked = (o: Order, all: boolean) => {
    if (!all) return;
    db.orderItems.filter((i) => i.orderID === o.orderID).forEach((i) => (i.pickedQuantity = i.quantity));
  };

  const created = (o: Order, versions = 1) => {
    event(o, o.createdTime, `Order created by client ${clientName(o)}.`);
    event(o, o.createdTime, "Delivery details version 1 saved.");
    o.deliveryDetailsVersionCount = versions;
  };

  const pay = (o: Order, started: number, accepted: number | null): Payment => {
    const payment: Payment = {
      paymentID: paymentID++,
      orderID: o.orderID,
      amount: o.totalAmount,
      paymentStatus: accepted === null ? "PENDING" : "ACCEPTED",
      createdTime: started,
      endedTime: accepted,
    };
    db.payments.push(payment);
    event(o, started, `Checkout started (${formatMoney(o.totalAmount)}).`);
    if (accepted !== null) event(o, accepted, "Payment accepted.");
    return payment;
  };

  const pickTask = (o: Order, worker: string, assigned: number, started: number | null, packed: number | null): PickingTask => {
    const task: PickingTask = {
      pickingTaskID: taskID++,
      orderID: o.orderID,
      taskStatus: packed ? "PACKED" : "IN_PROGRESS",
      startedTime: started,
      packedTime: packed,
    };
    db.pickingTasks.push(task);
    db.pickingAssignments.push({
      assignmentID: assignmentID++,
      pickingTaskID: task.pickingTaskID,
      workerUsername: worker,
      assignedTime: assigned,
      endedTime: packed,
      assignedBy: null,
      reason: null,
    });
    event(o, assigned, `Picking task assigned to ${worker}.`);
    if (started) event(o, started, "Picking started.");
    if (packed) {
      event(o, packed, "Packing confirmed.");
      setPicked(o, true);
    }
    return task;
  };

  const pack = (o: Order, responsible: string | null): DeliveryPackage => {
    const pkg: DeliveryPackage = {
      packageID: packageID++,
      packageCode: `PKG-${o.orderReference.slice(4)}`,
      orderID: o.orderID,
      location: "WAREHOUSE",
      heldBy: null,
      responsibleWorker: responsible,
      lastReassignedTime: null,
      lastReassignedBy: null,
      returnedTime: null,
      lastReminderTime: null,
    };
    db.packages.push(pkg);
    return pkg;
  };

  const pickUp = (o: Order, pkg: DeliveryPackage, driver: string, time: number) => {
    pkg.location = "DELIVERY";
    pkg.heldBy = driver;
    pkg.responsibleWorker = pkg.responsibleWorker ?? driver;
    event(o, time, "Package event: Picked up.", driver);
  };

  const attempt = (
    o: Order,
    driver: string,
    started: number,
    ended: number | null,
    outcome: DeliveryAttempt["outcome"],
    reason: string | null = null,
    note: string | null = null,
  ) => {
    db.attempts.push({ attemptID: attemptID++, orderID: o.orderID, workerUsername: driver, startedTime: started, endedTime: ended, outcome, reason, note });
    event(o, started, `Delivery attempt started by ${driver}.`);
    if (ended !== null && outcome === "DELIVERED") event(o, ended, "Delivery attempt ended: Delivered.");
    if (ended !== null && outcome === "FAILED") event(o, ended, `Delivery attempt ended: Failed. Reason: ${reason}.`);
  };

  const giveBack = (o: Order, pkg: DeliveryPackage, driver: string, requested: number, receivedBy: string, received: number) => {
    event(o, requested, "Package event: Return requested.", driver);
    event(o, received, "Package event: Return received.", receivedBy);
    pkg.location = "WAREHOUSE";
    pkg.heldBy = null;
    pkg.returnedTime = received;
  };

  const instruct = (
    o: Order,
    type: InstructionType,
    submitted: number,
    source: ClientInstruction["source"] = "TRACKING_PAGE",
    recordedBy: string | null = null,
    identity: string | null = null,
  ): ClientInstruction => {
    const instruction: ClientInstruction = {
      instructionID: instructionTemp++,
      orderID: o.orderID,
      instructionType: type,
      instructionStatus: "PENDING",
      source,
      submittedTime: submitted,
      recordedBy,
      identityCheckMethod: identity,
      decidedBy: null,
      decidedTime: null,
      note: null,
    };
    db.instructions.push(instruction);
    const word = type === "REDELIVER" ? "Redeliver" : "Cancel";
    event(o, submitted, `Client request: ${word} (entered through ${source === "TRACKING_PAGE" ? "Tracking page" : "Admin"}).`);
    return instruction;
  };

  const decide = (o: Order, instruction: ClientInstruction, status: "APPROVED" | "REJECTED", by: string, time: number, note: string | null = null) => {
    instruction.instructionStatus = status;
    instruction.decidedBy = by;
    instruction.decidedTime = time;
    instruction.note = note;
    if (status === "APPROVED" && instruction.instructionType === "REDELIVER") event(o, time, "Redelivery approved.", by);
    if (status === "APPROVED" && instruction.instructionType === "CANCEL") event(o, time, "Cancellation approved.", by);
    if (status === "REJECTED") event(o, time, `Client request rejected. Reason: ${note}.`, by);
  };

  const refund = (
    o: Order,
    payment: Payment,
    id: number,
    status: RefundStatus,
    createdAt: number,
    completed: number | null,
    by: string,
    lastCheck: number | null = null,
    answer: ProviderAnswer | null = null,
  ): Refund => {
    const row: Refund = {
      refundID: id,
      paymentID: payment.paymentID,
      orderID: o.orderID,
      amount: payment.amount,
      refundStatus: status,
      createdTime: createdAt,
      completedTime: completed,
      requestedBy: by,
      lastCheckTime: lastCheck,
      lastProviderAnswer: answer,
      providerAnswer: status === "REQUESTED" ? "PENDING" : (status as ProviderAnswer),
      replacedByRefundID: null,
    };
    db.refunds.push(row);
    event(o, createdAt, `Refund ${id} requested (${formatMoney(payment.amount)}).`);
    if (status === "REFUNDED" && completed) event(o, completed, `Refund ${id} confirmed by the payment provider.`);
    if (status === "FAILED") event(o, completed ?? createdAt + 50 * MIN, `Refund ${id} failed at the payment provider.`);
    return row;
  };

  const shortage = (
    o: Order,
    id: number,
    code: string,
    missing: number,
    ordered: number,
    reported: number,
    by: string,
    note: string | null,
  ): ShortageCase => {
    const row: ShortageCase = {
      shortageCaseID: id,
      orderID: o.orderID,
      productID: sku(code).productID,
      missingQuantity: missing,
      orderedQuantity: ordered,
      caseStatus: "OPEN",
      reportedTime: reported,
      reportedBy: by,
      note,
      decision: null,
      decidedBy: null,
      decidedTime: null,
      decisionReason: null,
      refundNote: null,
    };
    db.shortageCases.push(row);
    event(o, reported, `Shortage reported: ${missing} of ${code}.`, by);
    return row;
  };

  const clientName = (o: Order) => db.users.find((u) => u.userID === o.clientUserID)?.username ?? "unknown";

  const add = (key: string, createdText: string | number, client: string, build: (o: Order) => void) => {
    specs.push({ key, created: typeof createdText === "number" ? createdText : T(createdText), client, build });
  };

  // Generic flows ------------------------------------------------------------------------------
  const ACTIVE_ITEMS = db.products.filter((p) => p.listingStatus === "ACTIVE" && !["SKU-1002", "SKU-1005", "SKU-1017"].includes(p.SKU));
  const randomItems = (): Array<[string, number]> => {
    const count = pick([1, 1, 2, 2, 2, 3, 3, 4]);
    const chosen = new Set<string>();
    while (chosen.size < count) chosen.add(pick(ACTIVE_ITEMS).SKU);
    return [...chosen].map((code) => [code, pick([1, 1, 1, 2, 2, 3])]);
  };
  const PICKERS_BY_DATE = (time: number) =>
    [
      "wh_picker_01",
      "wh_picker_02",
      ...(time > T("2026-08-21 00:00") ? ["wh_picker_04"] : []),
      ...(time > T("2026-09-16 00:00") && time < T("2026-11-11 17:45") ? ["wh_picker_05"] : []),
      ...(time < T("2026-10-30 10:30") ? ["wh_old_account"] : []),
    ] as string[];
  const DRIVERS_BY_DATE = (time: number) =>
    ["dl_driver_01", "dl_driver_02", ...(time > T("2026-09-21 00:00") ? ["dl_driver_03"] : []), ...(time > T("2026-10-03 00:00") ? ["dl_driver_04"] : [])];

  /** A full journey from creation to delivery; maybe with one failed attempt and a redelivery. */
  const deliveredFlow = (o: Order, withFailure: boolean) => {
    items(o, randomItems());
    const versions = withFailure && chance(0.5) ? 2 : 1;
    created(o, versions);
    const t0 = o.createdTime;
    const paidAt = t0 + between(2, 6) * MIN;
    pay(o, t0 + between(1, 2) * MIN, paidAt);
    const picker = pick(PICKERS_BY_DATE(paidAt));
    const assigned = paidAt + between(30, 300) * MIN;
    const started = assigned + between(4, 15) * MIN;
    const packed = started + between(8, 35) * MIN;
    pickTask(o, picker, assigned, started, packed);
    const driver = pick(DRIVERS_BY_DATE(packed));
    const pkg = pack(o, driver);
    let t = packed + between(60, 900) * MIN;
    pickUp(o, pkg, driver, t);
    if (withFailure) {
      const s = t + between(20, 140) * MIN;
      const e = s + between(10, 45) * MIN;
      const reason = pick(["Client not home", "Client not home", "Unable to contact client", "Access code needed"]);
      attempt(o, driver, s, e, "FAILED", reason, null);
      const back = e + between(90, 300) * MIN;
      giveBack(o, pkg, driver, e + between(20, 60) * MIN, pick(PICKERS_BY_DATE(back)), back);
      const ask = back + between(60, 900) * MIN;
      if (versions === 2) event(o, ask - 5 * MIN, "Delivery details version 2 saved.");
      const instruction = instruct(o, "REDELIVER", ask);
      const decidedAt = ask + between(20, 240) * MIN;
      decide(o, instruction, "APPROVED", pick(["admin_main", "admin_main", "admin_second"]), decidedAt);
      t = decidedAt + between(120, 900) * MIN;
      pickUp(o, pkg, driver, t);
    }
    const s = t + between(20, 160) * MIN;
    const e = s + between(8, 40) * MIN;
    attempt(o, driver, s, e, "DELIVERED");
    pkg.heldBy = null;
    o.orderStatus = "DELIVERED";
  };

  /** Up to (and including) the given stage, with times that end before the sample moment. */
  const activeFlow = (
    o: Order,
    stage: OrderStatus,
    options: { picker?: string; driver?: string; items?: Array<[string, number]>; times?: number[] } = {},
  ) => {
    items(o, options.items ?? randomItems());
    created(o);
    o.orderStatus = stage;
    const t0 = o.createdTime;
    const step = options.times ?? [];
    const at = (index: number, fallback: number) => step[index] ?? fallback;
    if (stage === "AWAITING_PAYMENT") {
      o.paymentDeadline = t0 + 60 * MIN;
      if (step[0]) pay(o, step[0], null);
      return;
    }
    const paidAt = at(1, t0 + 3 * MIN);
    pay(o, at(0, t0 + 1 * MIN), paidAt);
    if (stage === "READY_FOR_PICKING") return;
    const picker = options.picker ?? pick(["wh_picker_02", "wh_picker_04"]);
    const assigned = at(2, paidAt + 40 * MIN);
    const started = at(3, assigned + 6 * MIN);
    const packedAt = stage === "PICKING" ? null : at(4, started + 18 * MIN);
    pickTask(o, picker, assigned, started, packedAt);
    if (stage === "PICKING") return;
    const driver = options.driver ?? pick(["dl_driver_01", "dl_driver_03", "dl_driver_04"]);
    const pkg = pack(o, driver);
    if (stage === "READY_FOR_PICKUP") return;
    pickUp(o, pkg, driver, at(5, (packedAt ?? started) + 50 * MIN));
    if (stage === "SHIPPED") return;
    attempt(o, driver, at(6, (packedAt ?? started) + 110 * MIN), null, null);
  };

  // ----- delivered history (211 orders from mid August to 9 November; with K5 below, 212 delivered)
  const deliveredStart = T("2026-08-15 09:00");
  const deliveredEnd = T("2026-11-09 12:00");
  const failureIndexes = new Set([7, 19, 33, 48, 61, 77, 90, 104, 118, 131, 147, 160, 176, 189, 203]);
  for (let i = 0; i < 211; i += 1) {
    const time = deliveredStart + Math.floor(((deliveredEnd - deliveredStart) * (i + rand() * 0.8)) / 211);
    const hour = new Date(time).getHours();
    const createdAt = hour < 8 ? time + (8 - hour) * HOUR + between(0, 50) * MIN : time;
    const eligible = clients.filter(
      (c) => c.createdTime < createdAt && c.username !== "client_0457" && !(c.username === "client_0470" && createdAt > T("2026-11-06 11:45")),
    );
    const client = pick(eligible.length ? eligible : clients).username;
    const withFailure = failureIndexes.has(i);
    add(`D${i}`, createdAt, client, (o) => deliveredFlow(o, withFailure));
  }
  // TEST-0005 of the design note: a delivered order of client_0461
  add("K5", "2026-11-09 14:02", "client_0461", (o) => {
    items(o, [["SKU-1011", 1], ["SKU-1029", 1]]);
    created(o);
    pay(o, T("2026-11-09 14:03"), T("2026-11-09 14:05"));
    pickTask(o, "wh_picker_04", T("2026-11-09 14:40"), T("2026-11-09 14:46"), T("2026-11-09 15:02"));
    const pkg = pack(o, "dl_driver_03");
    pickUp(o, pkg, "dl_driver_03", T("2026-11-09 15:30"));
    attempt(o, "dl_driver_03", T("2026-11-09 15:45"), T("2026-11-09 16:02"), "DELIVERED");
    pkg.heldBy = null;
    o.orderStatus = "DELIVERED";
  });

  // ----- historical cancellations with refunds 1 to 11 (refund 6 failed and was replaced by refund 7)
  const historicalRefunds: Array<{ created: string; kind: "window" | "shortage" | "failed"; ids: number[]; client: string }> = [
    { created: "2026-08-24 10:15", kind: "window", ids: [1], client: "client_0451" },
    { created: "2026-09-03 19:40", kind: "shortage", ids: [2], client: "client_0454" },
    { created: "2026-09-12 13:05", kind: "window", ids: [3], client: "client_0456" },
    { created: "2026-09-21 11:30", kind: "failed", ids: [4], client: "client_0460" },
    { created: "2026-09-29 08:55", kind: "window", ids: [5], client: "client_0452" },
    { created: "2026-10-07 16:20", kind: "shortage", ids: [6, 7], client: "client_0463" },
    { created: "2026-10-15 12:10", kind: "window", ids: [8], client: "client_0469" },
    { created: "2026-10-22 09:45", kind: "failed", ids: [9], client: "client_0471" },
    { created: "2026-10-30 14:30", kind: "window", ids: [10], client: "client_0466" },
    { created: "2026-11-05 10:05", kind: "shortage", ids: [11], client: "client_0472" },
  ];
  const historicalShortageIDs = new Map([["2026-09-03 19:40", 2], ["2026-10-07 16:20", 3], ["2026-11-05 10:05", 4]]);
  historicalRefunds.forEach((h, index) => {
    add(`HR${index}`, h.created, h.client, (o) => {
      items(o, randomItems());
      created(o);
      const t0 = o.createdTime;
      const payment = pay(o, t0 + 2 * MIN, t0 + 4 * MIN);
      o.orderStatus = "CANCELLED";
      if (h.kind === "window") {
        const cancelAt = t0 + between(30, 200) * MIN;
        event(o, cancelAt, `Order cancelled by client ${h.client} within the cancellation window.`);
        refund(o, payment, h.ids[0], "REFUNDED", cancelAt, cancelAt + between(60, 300) * MIN, "system");
      } else if (h.kind === "shortage") {
        const picker = pick(PICKERS_BY_DATE(t0));
        const assigned = t0 + 60 * MIN;
        pickTask(o, picker, assigned, assigned + 8 * MIN, null);
        const task = db.pickingTasks[db.pickingTasks.length - 1];
        const line = db.orderItems.find((i) => i.orderID === o.orderID)!;
        const product = db.products.find((p) => p.productID === line.productID)!;
        const reported = assigned + 30 * MIN;
        const caseRow = shortage(o, historicalShortageIDs.get(h.created)!, product.SKU, line.quantity, line.quantity, reported, picker, pick(["shelf is empty", "units arrived damaged", "label does not match the box"]));
        const decidedAt = reported + between(90, 600) * MIN;
        caseRow.caseStatus = "CANCELLED";
        caseRow.decision = "CANCEL_AND_REFUND";
        caseRow.decidedBy = "admin_main";
        caseRow.decidedTime = decidedAt;
        caseRow.decisionReason = "the supplier has no new stock this month";
        caseRow.refundNote = "Full refund requested.";
        task.taskStatus = "CANCELLED";
        db.pickingAssignments[db.pickingAssignments.length - 1].endedTime = decidedAt;
        event(o, decidedAt, "Order cancelled by admin_main. A full refund was requested.", "admin_main");
        if (h.ids.length === 2) {
          const failed = refund(o, payment, h.ids[0], "FAILED", decidedAt, decidedAt + 40 * MIN, "admin_main", decidedAt + 20 * HOUR, "FAILED");
          const retried = decidedAt + 21 * HOUR;
          failed.replacedByRefundID = h.ids[1];
          event(o, retried, `Refund ${h.ids[0]} retried by admin_second.`, "admin_second");
          refund(o, payment, h.ids[1], "REFUNDED", retried, retried + 3 * HOUR, "admin_second");
        } else {
          refund(o, payment, h.ids[0], "REFUNDED", decidedAt, decidedAt + between(60, 400) * MIN, "admin_main");
        }
      } else {
        const picker = pick(PICKERS_BY_DATE(t0));
        const assigned = t0 + 50 * MIN;
        pickTask(o, picker, assigned, assigned + 7 * MIN, assigned + 25 * MIN);
        const driver = pick(DRIVERS_BY_DATE(t0));
        const pkg = pack(o, driver);
        const up = assigned + 20 * HOUR;
        pickUp(o, pkg, driver, up);
        attempt(o, driver, up + 40 * MIN, up + 70 * MIN, "FAILED", "Client refused", "client did not want the order any more");
        giveBack(o, pkg, driver, up + 90 * MIN, picker, up + 5 * HOUR);
        const ask = up + 26 * HOUR;
        const instruction = instruct(o, "CANCEL", ask);
        const decidedAt = ask + 2 * HOUR;
        decide(o, instruction, "APPROVED", "admin_second", decidedAt);
        refund(o, payment, h.ids[0], "REFUNDED", decidedAt, decidedAt + 4 * HOUR, "admin_second");
      }
    });
  });

  // ----- unpaid cancellations (the payment deadline passed)
  ["2026-09-08 22:10", "2026-10-11 18:35", "2026-11-03 07:50"].forEach((when, index) => {
    add(`UC${index}`, when, ["client_0464", "client_0473", "client_0478"][index], (o) => {
      items(o, randomItems());
      created(o);
      o.paymentDeadline = o.createdTime + 60 * MIN;
      const payment = pay(o, o.createdTime + 2 * MIN, null);
      payment.paymentStatus = "FAILED";
      payment.endedTime = o.paymentDeadline;
      event(o, o.paymentDeadline, "Payment deadline passed. Order cancelled.", null);
      o.orderStatus = "CANCELLED";
    });
  });

  // ----- recent orders: the cases of the design note and the rest of the active orders --------

  // Delivery failed, returned 7 days ago, failed twice (flags Review and Final review)
  add("K1", "2026-11-02 15:30", "client_0450", (o) => {
    items(o, [["SKU-1043", 1], ["SKU-1044", 1]]);
    created(o, 2);
    pay(o, T("2026-11-02 15:31"), T("2026-11-02 15:33"));
    pickTask(o, "wh_picker_01", T("2026-11-03 09:00"), T("2026-11-03 09:10"), T("2026-11-03 09:25"));
    const pkg = pack(o, "dl_driver_02");
    pickUp(o, pkg, "dl_driver_02", T("2026-11-03 13:00"));
    attempt(o, "dl_driver_02", T("2026-11-03 14:10"), T("2026-11-03 14:40"), "FAILED", "Wrong address", "building number does not exist");
    giveBack(o, pkg, "dl_driver_02", T("2026-11-03 15:05"), "wh_picker_02", T("2026-11-03 18:00"));
    event(o, T("2026-11-03 19:20"), "Delivery details version 2 saved.");
    const first = instruct(o, "REDELIVER", T("2026-11-03 19:30"));
    decide(o, first, "APPROVED", "admin_second", T("2026-11-04 08:30"));
    pickUp(o, pkg, "dl_driver_02", T("2026-11-04 12:30"));
    attempt(o, "dl_driver_02", T("2026-11-04 13:20"), T("2026-11-04 13:50"), "FAILED", "Wrong address", "the new address is also wrong");
    giveBack(o, pkg, "dl_driver_02", T("2026-11-04 14:15"), "wh_picker_02", T("2026-11-04 17:30"));
    pkg.lastReminderTime = T("2026-11-08 10:00");
    event(o, T("2026-11-08 10:00"), "Reminder sent to the client.", "admin_main");
    o.orderStatus = "DELIVERY_FAILED";
  });

  // Delivery failed, returned 3 days ago (flag Reminder due)
  add("K2", "2026-11-07 09:40", "client_0453", (o) => {
    items(o, [["SKU-1069", 1], ["SKU-1031", 1]]);
    created(o);
    pay(o, T("2026-11-07 09:41"), T("2026-11-07 09:42"));
    pickTask(o, "wh_picker_02", T("2026-11-07 11:00"), T("2026-11-07 11:06"), T("2026-11-07 11:20"));
    const pkg = pack(o, "dl_driver_01");
    pickUp(o, pkg, "dl_driver_01", T("2026-11-08 09:30"));
    attempt(o, "dl_driver_01", T("2026-11-08 10:40"), T("2026-11-08 11:15"), "FAILED", "Client not home", null);
    giveBack(o, pkg, "dl_driver_01", T("2026-11-08 11:40"), "wh_picker_04", T("2026-11-08 16:40"));
    o.orderStatus = "DELIVERY_FAILED";
  });

  // Delivery failed, the client asks for cancellation
  add("K3", "2026-11-08 14:05", "client_0455", (o) => {
    items(o, [["SKU-1029", 1], ["SKU-1048", 1]]);
    created(o);
    pay(o, T("2026-11-08 14:06"), T("2026-11-08 14:08"));
    pickTask(o, "wh_picker_04", T("2026-11-08 15:00"), T("2026-11-08 15:10"), T("2026-11-08 15:30"));
    const pkg = pack(o, "dl_driver_02");
    pickUp(o, pkg, "dl_driver_02", T("2026-11-09 14:10"));
    attempt(o, "dl_driver_02", T("2026-11-09 15:55"), T("2026-11-09 16:40"), "FAILED", "Client refused", "client says the order is a mistake");
    giveBack(o, pkg, "dl_driver_02", T("2026-11-09 17:20"), "wh_picker_02", T("2026-11-09 19:05"));
    instruct(o, "CANCEL", T("2026-11-10 09:30"));
    o.orderStatus = "DELIVERY_FAILED";
  });

  // Cancelled after a shortage; refund 12 failed
  add("K4", "2026-11-08 09:05", "client_0461", (o) => {
    items(o, [["SKU-1005", 1]]);
    created(o);
    const payment = pay(o, T("2026-11-08 09:06"), T("2026-11-08 09:07"));
    const task = pickTask(o, "wh_picker_01", T("2026-11-09 08:30"), T("2026-11-09 08:40"), null);
    const caseRow = shortage(o, 5, "SKU-1005", 1, 1, T("2026-11-09 09:05"), "wh_picker_01", "only damaged units on the shelf");
    caseRow.caseStatus = "CANCELLED";
    caseRow.decision = "CANCEL_AND_REFUND";
    caseRow.decidedBy = "admin_second";
    caseRow.decidedTime = T("2026-11-10 06:10");
    caseRow.decisionReason = "no restock date from the supplier";
    caseRow.refundNote = "Full refund requested.";
    task.taskStatus = "CANCELLED";
    db.pickingAssignments[db.pickingAssignments.length - 1].endedTime = T("2026-11-10 06:10");
    event(o, T("2026-11-10 06:10"), "Order cancelled by admin_second. A full refund was requested.", "admin_second");
    refund(o, payment, 12, "FAILED", T("2026-11-10 06:10"), T("2026-11-10 07:02"), "admin_second", T("2026-11-12 08:00"), "FAILED");
    o.orderStatus = "CANCELLED";
  });

  // Delivery failed; open shortage case 9 blocks the redelivery an Admin recorded
  add("K6", "2026-11-09 09:12", "client_0459", (o) => {
    items(o, [["SKU-1001", 1]]);
    created(o);
    pay(o, T("2026-11-09 09:13"), T("2026-11-09 09:15"));
    pickTask(o, "wh_picker_01", T("2026-11-09 10:00"), T("2026-11-09 10:05"), T("2026-11-09 10:12"));
    const pkg = pack(o, "dl_driver_01");
    pickUp(o, pkg, "dl_driver_01", T("2026-11-10 08:45"));
    attempt(o, "dl_driver_01", T("2026-11-10 09:20"), T("2026-11-10 10:05"), "FAILED", "Unable to contact client", "phone switched off");
    giveBack(o, pkg, "dl_driver_01", T("2026-11-10 11:30"), "wh_picker_02", T("2026-11-11 17:50"));
    shortage(o, 9, "SKU-1001", 1, 1, T("2026-11-11 18:05"), "wh_picker_02", "box was crushed on the way back");
    instruct(o, "REDELIVER", T("2026-11-12 08:58"), "ADMIN_MANUAL", "admin_main", "caller answered the account questions");
    o.orderStatus = "DELIVERY_FAILED";
  });

  // Delivery failed; the client asks for redelivery on the tracking page
  add("K7", "2026-11-10 11:20", "client_0462", (o) => {
    items(o, [["SKU-1002", 1]]);
    created(o, 2);
    pay(o, T("2026-11-10 11:21"), T("2026-11-10 11:23"));
    pickTask(o, "wh_picker_02", T("2026-11-10 12:00"), T("2026-11-10 12:05"), T("2026-11-10 12:20"));
    const pkg = pack(o, "dl_driver_01");
    pickUp(o, pkg, "dl_driver_01", T("2026-11-10 13:05"));
    attempt(o, "dl_driver_01", T("2026-11-10 13:40"), T("2026-11-10 14:22"), "FAILED", "Client not home", "nobody opened the door, call 5550 0000 1234");
    giveBack(o, pkg, "dl_driver_01", T("2026-11-10 16:10"), "wh_picker_02", T("2026-11-10 17:30"));
    event(o, T("2026-11-11 08:05"), "Delivery details version 2 saved.");
    instruct(o, "REDELIVER", T("2026-11-11 08:10"));
    o.orderStatus = "DELIVERY_FAILED";
  });

  // Picking; shortage case 8 waits for restock
  add("K8", "2026-11-11 13:50", "client_0458", (o) => {
    items(o, [["SKU-1001", 4, 3]]);
    created(o);
    pay(o, T("2026-11-11 13:51"), T("2026-11-11 13:52"));
    pickTask(o, "wh_picker_02", T("2026-11-11 14:20"), T("2026-11-11 14:30"), null);
    const caseRow = shortage(o, 8, "SKU-1001", 1, 4, T("2026-11-11 15:10"), "wh_picker_02", "shelf is empty");
    caseRow.caseStatus = "WAITING_RESTOCK";
    caseRow.decision = "WAIT_FOR_RESTOCK";
    caseRow.decidedBy = "admin_main";
    caseRow.decidedTime = T("2026-11-11 16:00");
    caseRow.decisionReason = "new stock arrives on Friday";
    event(o, T("2026-11-11 16:00"), "Shortage case 8: wait for restock. The client was told about the delay.", "admin_main");
    o.orderStatus = "PICKING";
  });

  // Out for delivery
  add("K9", "2026-11-11 15:45", "client_0461", (o) => {
    activeFlow(o, "OUT_FOR_DELIVERY", {
      picker: "wh_picker_04",
      driver: "dl_driver_01",
      items: [["SKU-1001", 1]],
      times: [T("2026-11-11 15:46"), T("2026-11-11 15:47"), T("2026-11-11 16:30"), T("2026-11-11 16:36"), T("2026-11-11 16:50"), T("2026-11-12 07:45"), T("2026-11-12 08:30")],
    });
  });

  // Ready for redelivery: approved today at 08:12 (one of today's decisions)
  add("K10", "2026-11-10 18:20", "client_0462", (o) => {
    items(o, [["SKU-1024", 2], ["SKU-1025", 2], ["SKU-1023", 1]]);
    created(o);
    pay(o, T("2026-11-10 18:21"), T("2026-11-10 18:22"));
    pickTask(o, "wh_picker_04", T("2026-11-11 08:30"), T("2026-11-11 08:40"), T("2026-11-11 08:55"));
    const pkg = pack(o, "dl_driver_03");
    pickUp(o, pkg, "dl_driver_03", T("2026-11-11 10:00"));
    attempt(o, "dl_driver_03", T("2026-11-11 11:05"), T("2026-11-11 11:30"), "FAILED", "Client not home", null);
    giveBack(o, pkg, "dl_driver_03", T("2026-11-11 12:00"), "wh_picker_02", T("2026-11-11 15:20"));
    const instruction = instruct(o, "REDELIVER", T("2026-11-11 19:45"));
    decide(o, instruction, "APPROVED", "admin_main", T("2026-11-12 08:12"));
    o.orderStatus = "READY_FOR_REDELIVERY";
  });

  // Picking; shortage case 10 is open
  add("K11", "2026-11-12 07:10", "client_0462", (o) => {
    items(o, [["SKU-1002", 3, 2]]);
    created(o);
    pay(o, T("2026-11-12 07:11"), T("2026-11-12 07:12"));
    pickTask(o, "wh_picker_02", T("2026-11-12 07:30"), T("2026-11-12 07:42"), null);
    shortage(o, 10, "SKU-1002", 1, 3, T("2026-11-12 08:40"), "wh_picker_02", "1 unit is cracked");
    o.orderStatus = "PICKING";
  });

  // Awaiting payment
  add("K12", "2026-11-12 08:30", "client_0461", (o) => {
    activeFlow(o, "AWAITING_PAYMENT", { items: [["SKU-1029", 1], ["SKU-1077", 1]], times: [T("2026-11-12 08:31")] });
  });

  // Awaiting payment, not paid, with an open shortage case 11
  add("K13", "2026-11-12 08:10", "client_0465", (o) => {
    activeFlow(o, "AWAITING_PAYMENT", { items: [["SKU-1005", 2]] });
    o.paymentDeadline = T("2026-11-12 10:10");
    shortage(o, 11, "SKU-1005", 2, 2, T("2026-11-12 09:02"), "wh_picker_02", null);
  });

  // Picking, 1 of 4 picked, with scan rows
  add("K14", "2026-11-12 08:40", "client_0463", (o) => {
    items(o, [["SKU-1001", 2, 1], ["SKU-1002", 1, 0], ["SKU-1009", 1, 0]]);
    created(o);
    pay(o, T("2026-11-12 08:41"), T("2026-11-12 08:42"));
    pickTask(o, "wh_picker_02", T("2026-11-12 08:50"), T("2026-11-12 08:55"), null);
    o.orderStatus = "PICKING";
  });

  // Picking by a worker who is no longer active
  add("K15", "2026-11-11 16:00", "client_0464", (o) => {
    items(o, [["SKU-1023", 2]]);
    created(o);
    pay(o, T("2026-11-11 16:01"), T("2026-11-11 16:02"));
    pickTask(o, "wh_picker_05", T("2026-11-11 16:20"), T("2026-11-11 16:31"), null);
    o.orderStatus = "PICKING";
  });

  // Delivery failed this morning; the banned driver still holds the package
  add("K16", "2026-11-11 15:40", "client_0466", (o) => {
    items(o, [["SKU-1035", 1]]);
    created(o);
    pay(o, T("2026-11-11 15:41"), T("2026-11-11 15:42"));
    pickTask(o, "wh_picker_04", T("2026-11-11 16:40"), T("2026-11-11 16:45"), T("2026-11-11 17:00"));
    const pkg = pack(o, "dl_driver_02");
    pickUp(o, pkg, "dl_driver_02", T("2026-11-12 07:30"));
    attempt(o, "dl_driver_02", T("2026-11-12 07:55"), T("2026-11-12 08:20"), "FAILED", "Client not home", null);
    o.orderStatus = "DELIVERY_FAILED";
  });

  // Shipped; reassigned to another driver (record only until the package changes hands)
  add("K17", "2026-11-11 10:30", "client_0467", (o) => {
    activeFlow(o, "SHIPPED", {
      picker: "wh_picker_02",
      driver: "dl_driver_01",
      items: [["SKU-1040", 1]],
      times: [T("2026-11-11 10:31"), T("2026-11-11 10:33"), T("2026-11-11 10:50"), T("2026-11-11 10:58"), T("2026-11-11 11:30"), T("2026-11-11 11:45")],
    });
    const pkg = db.packages[db.packages.length - 1];
    pkg.responsibleWorker = "dl_driver_03";
    pkg.lastReassignedTime = T("2026-11-11 12:00");
    pkg.lastReassignedBy = "admin_second";
    event(o, T("2026-11-11 12:00"), "Delivery reassigned to dl_driver_03.", "admin_second");
  });

  // Cancelled by the client in the cancellation window; refund 15 pending
  add("K18", "2026-11-11 21:40", "client_0468", (o) => {
    items(o, [["SKU-1003", 1]]);
    created(o);
    const payment = pay(o, T("2026-11-11 21:41"), T("2026-11-11 21:42"));
    event(o, T("2026-11-12 04:10"), "Order cancelled by client client_0468 within the cancellation window.");
    refund(o, payment, 15, "PENDING", T("2026-11-12 04:10"), null, "system", T("2026-11-12 08:05"), "PENDING");
    o.orderStatus = "CANCELLED";
  });

  // Refund 13: cancelled after a shortage on 10 November
  add("R13", "2026-11-10 09:25", "client_0475", (o) => {
    items(o, [["SKU-1054", 1], ["SKU-1053", 1]]);
    created(o);
    const payment = pay(o, T("2026-11-10 09:26"), T("2026-11-10 09:28"));
    const task = pickTask(o, "wh_picker_04", T("2026-11-10 12:30"), T("2026-11-10 12:36"), null);
    const caseRow = shortage(o, 6, "SKU-1054", 1, 1, T("2026-11-10 13:40"), "wh_picker_04", "lid is missing from the box");
    caseRow.caseStatus = "CANCELLED";
    caseRow.decision = "CANCEL_AND_REFUND";
    caseRow.decidedBy = "admin_main";
    caseRow.decidedTime = T("2026-11-10 14:30");
    caseRow.decisionReason = "client asked not to wait for a replacement";
    caseRow.refundNote = "Full refund requested.";
    task.taskStatus = "CANCELLED";
    db.pickingAssignments[db.pickingAssignments.length - 1].endedTime = T("2026-11-10 14:30");
    event(o, T("2026-11-10 14:30"), "Order cancelled by admin_main. A full refund was requested.", "admin_main");
    refund(o, payment, 13, "REFUNDED", T("2026-11-10 14:30"), T("2026-11-10 18:00"), "admin_main");
    o.orderStatus = "CANCELLED";
  });

  // Refund 14: cancelled in the window on 11 November
  add("R14", "2026-11-11 08:40", "client_0477", (o) => {
    items(o, [["SKU-1086", 1], ["SKU-1084", 1]]);
    created(o);
    const payment = pay(o, T("2026-11-11 08:41"), T("2026-11-11 08:43"));
    event(o, T("2026-11-11 09:20"), "Order cancelled by client client_0477 within the cancellation window.");
    refund(o, payment, 14, "REFUNDED", T("2026-11-11 09:20"), T("2026-11-11 11:00"), "system");
    o.orderStatus = "CANCELLED";
  });

  // Today's second decision: cancellation approved at 08:47, refund 16 confirmed at 09:05
  add("C1", "2026-11-06 12:15", "client_0469", (o) => {
    items(o, [["SKU-1059", 1], ["SKU-1044", 2]]);
    created(o);
    const payment = pay(o, T("2026-11-06 12:16"), T("2026-11-06 12:18"));
    pickTask(o, "wh_picker_01", T("2026-11-06 13:30"), T("2026-11-06 13:36"), T("2026-11-06 13:55"));
    const pkg = pack(o, "dl_driver_04");
    pickUp(o, pkg, "dl_driver_04", T("2026-11-07 09:10"));
    attempt(o, "dl_driver_04", T("2026-11-07 10:20"), T("2026-11-07 10:45"), "FAILED", "Access code needed", "gate needs a code, no answer at the intercom");
    giveBack(o, pkg, "dl_driver_04", T("2026-11-07 11:10"), "wh_picker_04", T("2026-11-07 15:30"));
    const instruction = instruct(o, "CANCEL", T("2026-11-11 20:05"));
    decide(o, instruction, "APPROVED", "admin_main", T("2026-11-12 08:47"));
    event(o, T("2026-11-12 08:47"), "Order cancelled. A full refund was requested.", "admin_main");
    refund(o, payment, 16, "REFUNDED", T("2026-11-12 08:47"), T("2026-11-12 09:05"), "admin_main");
    o.orderStatus = "CANCELLED";
  });

  // Ready for pickup: one after shortage case 7 was resolved this morning
  add("RU1", "2026-11-11 09:05", "client_0471", (o) => {
    items(o, [["SKU-1018", 2], ["SKU-1035", 1]]);
    created(o);
    pay(o, T("2026-11-11 09:06"), T("2026-11-11 09:08"));
    pickTask(o, "wh_picker_02", T("2026-11-11 09:20"), T("2026-11-11 09:26"), T("2026-11-12 07:50"));
    const caseRow = shortage(o, 7, "SKU-1018", 1, 2, T("2026-11-11 09:30"), "wh_picker_02", "one candle jar is chipped");
    caseRow.caseStatus = "RESOLVED";
    caseRow.decision = "WAIT_FOR_RESTOCK";
    caseRow.decidedBy = "admin_second";
    caseRow.decidedTime = T("2026-11-11 10:00");
    caseRow.decisionReason = "replacement jars arrive tomorrow morning";
    event(o, T("2026-11-11 10:00"), "Shortage case 7: wait for restock. The client was told about the delay.", "admin_second");
    event(o, T("2026-11-12 07:20"), "Shortage case 7 resolved after restock.", "wh_picker_02");
    pack(o, "dl_driver_04");
    o.orderStatus = "READY_FOR_PICKUP";
  });
  add("RU2", "2026-11-12 06:40", "client_0474", (o) => {
    activeFlow(o, "READY_FOR_PICKUP", {
      picker: "wh_picker_04",
      driver: "dl_driver_03",
      times: [T("2026-11-12 06:41"), T("2026-11-12 06:43"), T("2026-11-12 07:58"), T("2026-11-12 08:04"), T("2026-11-12 08:30")],
    });
  });

  // Ready for picking (paid, waiting for a picker)
  ["2026-11-11 19:05", "2026-11-11 22:30", "2026-11-12 07:35", "2026-11-12 08:05", "2026-11-12 08:48", "2026-11-12 09:01"].forEach((when, index) => {
    add(`RP${index}`, when, ["client_0472", "client_0456", "client_0479", "client_0452", "client_0460", "client_0454"][index], (o) =>
      activeFlow(o, "READY_FOR_PICKING"),
    );
  });

  // Awaiting payment (two more)
  add("AP3", "2026-11-12 08:52", "client_0473", (o) => activeFlow(o, "AWAITING_PAYMENT", { times: [T("2026-11-12 08:53")] }));
  add("AP4", "2026-11-12 09:05", "client_0478", (o) => activeFlow(o, "AWAITING_PAYMENT"));

  // Shipped (four more) and out for delivery (six more)
  const shipped: Array<[string, string, string]> = [
    ["2026-11-11 11:15", "client_0451", "dl_driver_03"],
    ["2026-11-11 13:20", "client_0476", "dl_driver_04"],
    ["2026-11-11 17:45", "client_0453", "dl_driver_01"],
    ["2026-11-11 20:10", "client_0458", "dl_driver_03"],
  ];
  shipped.forEach(([when, client, driver], index) => {
    add(`SH${index}`, when, client, (o) => {
      const t0 = o.createdTime;
      activeFlow(o, "SHIPPED", {
        driver,
        times: [t0 + MIN, t0 + 3 * MIN, T("2026-11-12 06:10") + index * 9 * MIN, T("2026-11-12 06:16") + index * 9 * MIN, T("2026-11-12 06:35") + index * 9 * MIN, T("2026-11-12 08:05") + index * 11 * MIN],
      });
    });
  });
  const outForDelivery: Array<[string, string, string]> = [
    ["2026-11-10 20:30", "client_0454", "dl_driver_01"],
    ["2026-11-11 08:15", "client_0459", "dl_driver_03"],
    ["2026-11-11 09:50", "client_0465", "dl_driver_03"],
    ["2026-11-11 12:40", "client_0475", "dl_driver_04"],
    ["2026-11-11 14:05", "client_0479", "dl_driver_04"],
    ["2026-11-11 18:30", "client_0450", "dl_driver_01"],
  ];
  outForDelivery.forEach(([when, client, driver], index) => {
    add(`OD${index}`, when, client, (o) => {
      const t0 = o.createdTime;
      activeFlow(o, "OUT_FOR_DELIVERY", {
        driver,
        times: [
          t0 + MIN,
          t0 + 3 * MIN,
          T("2026-11-11 16:00") + index * 12 * MIN,
          T("2026-11-11 16:07") + index * 12 * MIN,
          T("2026-11-11 16:30") + index * 12 * MIN,
          T("2026-11-12 07:20") + index * 6 * MIN,
          T("2026-11-12 08:00") + index * 11 * MIN,
        ],
      });
    });
  });

  // ----- number the orders by creation time and fill them
  specs.sort((a, b) => a.created - b.created);
  specs.forEach((spec, index) => {
    const client = userByName.get(spec.client) ?? clients[0];
    const order: Order = {
      orderID: index + 1,
      orderReference: `ORD-${10001 + index}`,
      clientUserID: client.userID,
      orderStatus: "AWAITING_PAYMENT",
      createdTime: spec.created,
      paymentDeadline: null,
      totalAmount: 0,
      deliveryDetailsVersionCount: 1,
      isTest: false,
    };
    db.orders.push(order);
    specOrder.set(spec.key, order);
  });
  specs.forEach((spec) => spec.build(specOrder.get(spec.key)!));

  // Instruction numbers follow the time they were sent.
  db.instructions.sort((a, b) => a.submittedTime - b.submittedTime).forEach((row, index) => (row.instructionID = index + 1));
  db.refunds.sort((a, b) => a.refundID - b.refundID);
  db.shortageCases.sort((a, b) => a.shortageCaseID - b.shortageCaseID);
  db.events.sort((a, b) => a.time - b.time || a.eventID - b.eventID);

  // Picking scans: one accepted scan per picked unit, plus the scan story of K14 and some refusals
  const pickedTaskRows = db.pickingTasks.filter((t) => t.startedTime !== null && t.startedTime > NOW - 90 * DAY);
  for (const task of pickedTaskRows) {
    const order = db.orders.find((o) => o.orderID === task.orderID)!;
    if (order.orderID === specOrder.get("K14")!.orderID) continue;
    const worker = db.pickingAssignments.find((a) => a.pickingTaskID === task.pickingTaskID)!.workerUsername;
    let time = (task.startedTime ?? order.createdTime) + 2 * MIN;
    for (const line of db.orderItems.filter((i) => i.orderID === order.orderID)) {
      const product = db.products.find((p) => p.productID === line.productID)!;
      for (let unit = 0; unit < line.pickedQuantity; unit += 1) {
        db.pickingScans.push({ scanID: 0, time, orderID: order.orderID, workerUsername: worker, scannedText: product.SKU, productSKU: product.SKU, result: "ACCEPTED", reason: null });
        time += between(1, 3) * MIN;
        if (chance(0.04)) {
          db.pickingScans.push({ scanID: 0, time, orderID: order.orderID, workerUsername: worker, scannedText: pick(["4006381333931", "TRACK 7781 2290 4410", "PKG-LABEL"]), productSKU: null, result: "REJECTED", reason: "not part of this order" });
          time += MIN;
        }
      }
    }
  }
  const k14 = specOrder.get("K14")!;
  const k14Rows: Array<[string, string, string | null, PickingScanRow["result"], string | null]> = [
    ["2026-11-12 08:55", "SKU-1002", "SKU-1002", "ACCEPTED", null],
    ["2026-11-12 08:55", "SKU-1002", "SKU-1002", "REVERSED", "wrong colour picked"],
    ["2026-11-12 08:56", "5550 0000 1234", null, "REJECTED", "not part of this order"],
    ["2026-11-12 08:57", "SKU-1001", "SKU-1001", "ACCEPTED", null],
  ];
  for (const [when, text, product, result, reason] of k14Rows) {
    db.pickingScans.push({ scanID: 0, time: T(when), orderID: k14.orderID, workerUsername: "wh_picker_02", scannedText: text, productSKU: product, result, reason });
  }
  db.pickingScans.sort((a, b) => a.time - b.time).forEach((row, index) => (row.scanID = index + 1));
  const reversed = db.pickingScans.find((s) => s.result === "REVERSED")!;
  const reversedTarget = db.pickingScans.find((s) => s.orderID === reversed.orderID && s.result === "ACCEPTED" && s.productSKU === reversed.productSKU)!;
  reversed.reason = `reverses scan ${reversedTarget.scanID}`;

  // Pickup lists: claimed lists of the drivers who hold packages today
  const lists: PickupList[] = [
    { pickupListID: 1, claimedBy: "dl_driver_01", status: "CLAIMED" },
    { pickupListID: 2, claimedBy: "dl_driver_03", status: "CLAIMED" },
    { pickupListID: 3, claimedBy: "dl_driver_04", status: "CLAIMED" },
    { pickupListID: 4, claimedBy: "dl_driver_02", status: "CLOSED" },
  ];
  db.pickupLists.push(...lists);

  buildAuditLog(db, user);
  buildSecurityLog(db);

  db.logCleanup = { time: T("2026-11-12 03:05"), securityLogDeletedCount: 12, pickingScanHistoryDeletedCount: 0, auditLogDeletedCount: 0 };

  db.nextID = {
    user: db.users.length + 1,
    product: db.products.length + 1,
    order: db.orders.length + 1,
    orderItem: itemID,
    payment: paymentID,
    refund: Math.max(...db.refunds.map((r) => r.refundID)) + 1,
    pickingTask: taskID,
    assignment: assignmentID,
    package: packageID,
    attempt: attemptID,
    instruction: db.instructions.length + 1,
    shortageCase: Math.max(...db.shortageCases.map((s) => s.shortageCaseID)) + 1,
    event: eventID,
    audit: db.auditLog.length + 1,
    security: db.securityLog.length + 1,
    scan: db.pickingScans.length + 1,
    pickupList: 5,
    testOrder: 1,
  };
  return db;
}

// ---------------------------------------------------------------------------------------------
// Audit log: every Admin change of the sample world, plus the daily log clean-up
// ---------------------------------------------------------------------------------------------

function buildAuditLog(db: Database, user: (name: string) => User): void {
  const rows: Omit<AuditLogRow, "auditLogID">[] = [];
  const add = (time: number, actor: string, action: string, targetType: AuditTarget, targetID: string | number, oldValue: object | null, newValue: object | null) =>
    rows.push({ time, actorUsername: actor, action, targetType, targetID: String(targetID), oldValue: oldValue ? JSON.stringify(oldValue) : null, newValue: newValue ? JSON.stringify(newValue) : null, isTest: false });

  // Accounts created by an Admin
  for (const u of db.users) {
    if (u.username === "admin_main" || u.role === "CLIENT") continue;
    add(u.createdTime, "admin_main", "USER_CREATED", "USER", u.userID, null, { username: u.username, role: u.role });
  }
  // Products: the first catalogue import and the products added later
  add(T("2026-08-02 10:30"), "admin_main", "PRODUCT_IMPORTED", "PRODUCT", "-", null, { addedCount: 120, rejectedCount: 2, fileName: "catalogue-2026-08.csv" });
  db.products.slice(120).forEach((p) => add(p.createdTime, "admin_second", "PRODUCT_CREATED", "PRODUCT", p.productID, null, { SKU: p.SKU, name: p.name, currentPrice: p.currentPrice }));
  add(T("2026-09-18 11:02"), "admin_second", "PRODUCT_EDITED", "PRODUCT", 3, { currentPrice: 1180.0 }, { currentPrice: 1234.5 });
  add(T("2026-10-01 09:40"), "admin_main", "PRODUCT_DEACTIVATED", "PRODUCT", 65, { listingStatus: "ACTIVE" }, { listingStatus: "INACTIVE", reason: "seasonal item, back in spring" });
  add(T("2026-10-04 15:12"), "admin_main", "PRODUCT_EDITED", "PRODUCT", 17, { lowStockThreshold: 4 }, { lowStockThreshold: 6 });
  add(T("2026-10-20 10:05"), "admin_second", "PRODUCT_DEACTIVATED", "PRODUCT", 4, { listingStatus: "ACTIVE" }, { listingStatus: "INACTIVE", reason: "supplier stopped this tray" });
  add(T("2026-10-28 16:30"), "admin_main", "PRODUCT_DEACTIVATED", "PRODUCT", 113, { listingStatus: "ACTIVE" }, { listingStatus: "INACTIVE", reason: "colour discontinued" });
  add(T("2026-11-03 10:15"), "admin_second", "PRODUCT_EDITED", "PRODUCT", 28, { name: "Weekly planner 2026, A5" }, { name: "Weekly planner 2027, A5" });
  add(T("2026-11-12 08:40"), "admin_second", "PRODUCT_EDITED", "PRODUCT", 7, { currentPrice: 72.0 }, { currentPrice: 80.0 });

  // Account changes
  add(T("2026-10-12 14:20"), "admin_main", "USER_PASSWORD_RESET", "USER", user("dl_driver_03").userID, null, { identityCheckMethod: "staff card shown in person" });
  add(T("2026-10-30 10:20"), "admin_second", "USER_UNLOCKED", "USER", user("wh_picker_01").userID, null, null);
  add(T("2026-11-03 18:02"), "admin_main", "USER_DEACTIVATED", "USER", user("client_0457").userID, { accountStatus: "ACTIVE" }, { accountStatus: "INACTIVE", reason: "client asked to close the account", activePickingTaskCount: 0, openPickupListCount: 0, heldPackageCount: 0 });
  add(T("2026-11-06 11:45"), "admin_second", "USER_BANNED", "USER", user("client_0470").userID, { accountStatus: "ACTIVE" }, { accountStatus: "BANNED", reason: "three chargebacks with stolen cards", activePickingTaskCount: 0, openPickupListCount: 0, heldPackageCount: 0 });
  add(T("2026-11-11 15:30"), "admin_main", "USER_PASSWORD_RESET", "USER", user("client_0476").userID, null, { identityCheckMethod: "caller answered the account questions" });
  add(T("2026-11-11 17:20"), "admin_main", "USER_PASSWORD_RESET", "USER", user("wh_picker_04").userID, null, { identityCheckMethod: "staff card shown in person", note: "call 5550 0000 1234 if locked again" });
  add(T("2026-11-11 17:45"), "admin_main", "USER_DEACTIVATED", "USER", user("wh_picker_05").userID, { accountStatus: "ACTIVE" }, { accountStatus: "INACTIVE", reason: "contract ended today", activePickingTaskCount: 1, openPickupListCount: 0, heldPackageCount: 0 });
  add(T("2026-11-12 09:10"), "admin_main", "USER_BANNED", "USER", user("dl_driver_02").userID, { accountStatus: "ACTIVE" }, { accountStatus: "BANNED", reason: "kept the package of a failed delivery and stopped answering", activePickingTaskCount: 0, openPickupListCount: 0, heldPackageCount: 1 });

  // Decisions, read from the rows they changed
  for (const c of db.shortageCases) {
    if (!c.decidedBy || !c.decidedTime) continue;
    add(c.decidedTime, c.decidedBy, "SHORTAGE_CASE_DECIDED", "SHORTAGE_CASE", c.shortageCaseID, { caseStatus: "OPEN" }, { decision: c.decision, reason: c.decisionReason });
  }
  for (const i of db.instructions) {
    const ref = db.orders.find((o) => o.orderID === i.orderID)!.orderReference;
    if (i.source === "ADMIN_MANUAL" && i.recordedBy) {
      add(i.submittedTime, i.recordedBy, "CLIENT_INSTRUCTION_RECORDED_BY_ADMIN", "CLIENT_INSTRUCTION", i.instructionID, null, { instructionType: i.instructionType, source: i.source, identityCheckMethod: i.identityCheckMethod, orderReference: ref });
    }
    if (i.decidedBy && i.decidedTime) {
      add(i.decidedTime, i.decidedBy, "CLIENT_INSTRUCTION_DECIDED", "CLIENT_INSTRUCTION", i.instructionID, { instructionStatus: "PENDING" }, { instructionStatus: i.instructionStatus, orderReference: ref });
    }
  }
  for (const r of db.refunds) {
    if (r.replacedByRefundID) add(db.refunds.find((x) => x.refundID === r.replacedByRefundID)!.createdTime, "admin_second", "REFUND_RETRIED", "REFUND", r.refundID, { refundStatus: "FAILED" }, { newRefundID: r.replacedByRefundID });
    if (r.lastCheckTime) add(r.lastCheckTime, "admin_main", "REFUND_STATUS_CHECKED", "REFUND", r.refundID, null, { providerAnswer: r.lastProviderAnswer });
  }
  for (const p of db.packages) {
    if (p.lastReassignedBy && p.lastReassignedTime) add(p.lastReassignedTime, p.lastReassignedBy, "DELIVERY_REASSIGNED", "PACKAGE", p.packageID, { responsibleWorker: p.heldBy }, { responsibleWorker: p.responsibleWorker, reason: "driver is off sick tomorrow" });
    if (p.lastReminderTime) add(p.lastReminderTime, "admin_main", "CLIENT_REMINDER_SENT", "ORDER", p.orderID, null, { orderReference: db.orders.find((o) => o.orderID === p.orderID)!.orderReference });
  }

  // CSV downloads
  const downloads: Array<[string, string, string]> = [
    ["2026-09-30 17:05", "admin_main", "orders"],
    ["2026-10-01 09:12", "admin_second", "products"],
    ["2026-10-15 16:44", "admin_main", "users"],
    ["2026-10-31 17:10", "admin_main", "orders"],
    ["2026-11-02 10:25", "admin_second", "auditLog"],
    ["2026-11-09 12:00", "admin_main", "refunds"],
    ["2026-11-11 08:50", "admin_second", "failedDeliveryCases"],
    ["2026-11-11 16:20", "admin_main", "shortageCases"],
  ];
  for (const [when, actor, list] of downloads) add(T(when), actor, "CSV_DOWNLOADED", "SYSTEM", "CSV", null, { list, rowCount: between(12, 280) });

  // The daily clean-up at 03:05
  for (let day = T("2026-08-02 03:05"); day <= T("2026-11-12 03:05"); day += DAY) {
    const securityDeleted = day < T("2026-11-01 03:05") ? 0 : between(6, 14);
    add(day, "system", "LOG_CLEANUP_RUN", "LOG", "LOG_CLEANUP", null, {
      securityLogDeletedCount: day === T("2026-11-12 03:05") ? 12 : securityDeleted,
      pickingScanHistoryDeletedCount: 0,
      auditLogDeletedCount: 0,
    });
  }

  rows.sort((a, b) => a.time - b.time);
  db.auditLog = rows.map((row, index) => ({ ...row, auditLogID: index + 1 }));
}

// ---------------------------------------------------------------------------------------------
// Security log: logins, logouts, refused logins and the events of Admin actions (last 90 days)
// ---------------------------------------------------------------------------------------------

function buildSecurityLog(db: Database): void {
  const rows: Omit<SecurityLogRow, "securityLogID">[] = [];
  const add = (time: number, username: string, eventType: string, result: SecurityLogRow["result"], ip: string, details: string | null, agent: string) =>
    rows.push({ time, username, eventType, result, ipAddress: ip, details, userAgent: agent, isTest: false });

  const office = (n: number) => `192.0.2.${n}`;
  const depot = (n: number) => `198.51.100.${n}`;
  const mobile = (n: number) => `203.0.113.${n}`;
  const staffAgent: Record<string, string> = {
    admin_main: AGENT.chrome,
    admin_second: AGENT.firefox,
    wh_picker_01: AGENT.android,
    wh_picker_02: AGENT.android,
    wh_picker_04: AGENT.android,
    wh_picker_05: AGENT.android,
    wh_old_account: AGENT.edge,
    dl_driver_01: AGENT.iphone,
    dl_driver_02: AGENT.android,
    dl_driver_03: AGENT.iphone,
    dl_driver_04: AGENT.android,
  };
  const start = T("2026-08-14 07:00");
  for (let day = start; day < T("2026-11-12 00:00"); day += DAY) {
    const weekday = new Date(day).getDay();
    if (weekday === 0) continue;
    for (const u of db.users) {
      if (u.role === "CLIENT" || u.createdTime > day || u.username === "wh_picker_03") continue;
      if (u.username === "wh_old_account" && day > T("2026-10-30 00:00")) continue;
      if (u.username === "wh_picker_05" && day > T("2026-11-11 23:00")) continue;
      if (!chance(u.role === "ADMIN" ? 0.62 : 0.48)) continue;
      const ip = u.role === "ADMIN" ? office(between(10, 14)) : u.role === "WAREHOUSE_WORKER" ? depot(between(20, 60)) : mobile(between(30, 90));
      const loginAt = day + between(0, 150) * MIN;
      if (chance(0.06)) add(loginAt - 2 * MIN, u.username, "LOGIN_FAILURE", "FAILURE", ip, "wrong password", staffAgent[u.username]);
      add(loginAt, u.username, "LOGIN_SUCCESS", "SUCCESS", ip, null, staffAgent[u.username]);
      if (chance(0.7)) add(loginAt + between(240, 540) * MIN, u.username, "LOGOUT", "SUCCESS", ip, null, staffAgent[u.username]);
    }
    if (chance(0.55)) {
      const c = pick(
        db.users.filter(
          (u) =>
            u.role === "CLIENT" &&
            u.createdTime < day &&
            !(u.username === "client_0457" && day > T("2026-11-02 00:00")) &&
            !(u.username === "client_0470" && day > T("2026-11-06 00:00")),
        ),
      );
      if (c) add(day + between(300, 760) * MIN, c.username, "LOGIN_SUCCESS", "SUCCESS", mobile(between(100, 200)), null, pick([AGENT.iphone, AGENT.android, AGENT.mac]));
    }
  }
  // Clients log in to place their orders
  for (const o of db.orders) {
    if (o.createdTime < start) continue;
    const c = db.users.find((u) => u.userID === o.clientUserID)!;
    add(o.createdTime - between(2, 9) * MIN, c.username, "LOGIN_SUCCESS", "SUCCESS", mobile(between(100, 200)), null, pick([AGENT.iphone, AGENT.android, AGENT.mac]));
  }
  add(T("2026-11-12 08:50"), "client_0461", "LOGIN_FAILURE", "FAILURE", mobile(151), "wrong password", AGENT.mac);
  add(T("2026-11-12 08:51"), "client_0461", "LOGIN_FAILURE", "FAILURE", mobile(151), "wrong password", AGENT.mac);
  add(T("2026-11-11 08:31"), "client_0474", "LOGIN_FAILURE", "FAILURE", mobile(122), "wrong password", AGENT.iphone);
  add(T("2026-11-02 21:36"), "client_0457", "LOGIN_SUCCESS", "SUCCESS", mobile(140), null, AGENT.android);
  for (const u of db.users) {
    if (u.role !== "CLIENT" || u.mustChangePassword) continue;
    const newest = rows.filter((r) => r.username === u.username && r.eventType === "LOGIN_SUCCESS").sort((a, b) => b.time - a.time)[0];
    u.lastLoginTime = newest ? newest.time : null;
  }

  // The newest login of every account matches its "Last login"
  for (const u of db.users) {
    if (u.lastLoginTime === null) continue;
    const exists = rows.some((r) => r.username === u.username && r.eventType === "LOGIN_SUCCESS" && r.time === u.lastLoginTime);
    if (!exists) {
      const ip = u.role === "ADMIN" ? office(10) : u.role === "WAREHOUSE_WORKER" ? depot(44) : u.role === "CLIENT" ? mobile(150) : mobile(61);
      add(u.lastLoginTime, u.username, "LOGIN_SUCCESS", "SUCCESS", ip, null, staffAgent[u.username] ?? AGENT.iphone);
    }
  }
  for (let i = 0; i < 5; i += 1) add(T("2026-11-12 09:07") + i * 50 * 1000, "wh_picker_01", "LOGIN_FAILURE", "FAILURE", depot(44), "wrong password", AGENT.firefox);
  add(T("2026-11-12 09:11"), "wh_picker_01", "ACCOUNT_LOCKED", "FAILURE", depot(44), "5 wrong passwords", AGENT.firefox);
  add(T("2026-11-11 16:41"), "admin_second", "REJECTED_ADMIN_ACTION", "REJECTED", office(25), "adminBanUser: ADMIN_OWN_ACCOUNT_NOT_ALLOWED", AGENT.chrome);
  add(T("2026-11-12 08:40"), "wh_picker_03", "TEMPORARY_PASSWORD_ISSUED", "SUCCESS", office(10), "issued by admin_main", AGENT.chrome);
  add(T("2026-11-11 15:30"), "client_0476", "TEMPORARY_PASSWORD_ISSUED", "SUCCESS", office(10), "issued by admin_main", AGENT.chrome);
  add(T("2026-11-11 17:45"), "wh_picker_05", "ACCOUNT_DEACTIVATED", "SUCCESS", office(10), "by admin_main", AGENT.chrome);
  add(T("2026-11-11 17:45"), "wh_picker_05", "SESSION_REVOKED", "SUCCESS", office(10), "account deactivated", AGENT.chrome);
  add(T("2026-11-12 09:10"), "dl_driver_02", "ACCOUNT_BANNED", "SUCCESS", office(10), "by admin_main", AGENT.chrome);
  add(T("2026-11-12 09:10"), "dl_driver_02", "SESSION_REVOKED", "SUCCESS", office(10), "account banned", AGENT.chrome);
  add(T("2026-11-06 11:45"), "client_0470", "ACCOUNT_BANNED", "SUCCESS", office(25), "by admin_second", AGENT.firefox);
  add(T("2026-11-03 18:02"), "client_0457", "ACCOUNT_DEACTIVATED", "SUCCESS", office(10), "by admin_main", AGENT.chrome);
  add(T("2026-10-30 10:20"), "wh_picker_01", "ACCOUNT_UNLOCKED_BY_ADMIN", "SUCCESS", office(25), "by admin_second", AGENT.firefox);
  add(T("2026-11-10 21:14"), "client_0470", "LOGIN_FAILURE", "REJECTED", mobile(177), "account is banned", AGENT.android);
  add(T("2026-11-09 13:31"), "wh_picker_04", "ACCESS_DENIED", "REJECTED", depot(31), "/admin/admin-users.html", AGENT.android);
  add(T("2026-11-05 08:02"), "dl_driver_03", "PASSWORD_CHANGED", "SUCCESS", mobile(64), null, AGENT.iphone);

  const cutoff = T("2026-11-12 03:05") - 90 * DAY;
  rows.sort((a, b) => a.time - b.time);
  db.securityLog = rows.filter((r) => r.time >= cutoff).map((row, index) => ({ ...row, securityLogID: index + 1 }));
}

export const SAMPLE_NOW = NOW;
