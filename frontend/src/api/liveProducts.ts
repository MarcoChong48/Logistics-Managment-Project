import { adminAddress, failedAction, sendAdminMessage } from "./adminLists";
import { sendJson } from "./http";
import type { ActionResult, ListingStatus } from "./types";

const PRODUCTS = "/api/admin/products";

export interface Product {
  productID: number;
  SKU: string;
  name: string;
  currentPrice: number;
  lowStockThreshold: number;
  listingStatus: ListingStatus;
  physicalStock: number;
  reservedStock: number;
  adminSearchProductUnavailableStock: number;
  adminSearchProductAvailableStock: number;
  adminSearchProductReservationShortfall: number;
  adminSearchProductIsLowStock: boolean;
}

export interface SearchProducts {
  adminSearchProductRowList: Product[];
  adminSearchProductPageNumber: number;
  adminSearchProductTotalPageCount: number;
  adminSearchProductTotalRowCount: number;
}

export interface ProductImpact {
  adminPreviewProductImpactOpenOrderCount: number;
}

export function productsAddress(filters: { q: string; status: string; low: string }, page?: number) {
  return adminAddress(page === undefined ? `${PRODUCTS}/csv` : PRODUCTS, {
    adminSearchProductKeyword: filters.q,
    adminSearchProductListingStatus: filters.status,
    adminSearchProductIsLowStockOnly: filters.low === "true",
    adminSearchProductPageNumber: page,
  });
}

export const productImpactAddress = (id: number) => `${PRODUCTS}/${id}/impact`;
export const productQrAddress = (id: number) => `${PRODUCTS}/${id}/qr-code`;

interface ProductForm {
  SKU: string;
  name: string;
  price: string;
  lowBelow: string;
  status: ListingStatus;
}

const SKU_ERRORS = { ADMIN_SKU_INVALID: "SKU", ADMIN_SKU_TAKEN: "SKU" };

export function createProduct(form: ProductForm) {
  return sendAdminMessage("POST", PRODUCTS, {
    adminCreateProductSKU: form.SKU,
    adminCreateProductName: form.name,
    adminCreateProductCurrentPrice: form.price,
    adminCreateProductLowStockThreshold: form.lowBelow,
    adminCreateProductListingStatus: form.status,
  }, SKU_ERRORS);
}

export function editProduct(id: number, form: ProductForm) {
  return sendAdminMessage("PUT", `${PRODUCTS}/${id}`, {
    adminEditProductSKU: form.SKU,
    adminEditProductName: form.name,
    adminEditProductCurrentPrice: form.price,
    adminEditProductLowStockThreshold: form.lowBelow,
  }, SKU_ERRORS);
}

export function setProductListing(id: number, status: ListingStatus, reason: string) {
  return status === "ACTIVE" ?
    sendAdminMessage("POST", `${PRODUCTS}/${id}/activate`) :
    sendAdminMessage("POST", `${PRODUCTS}/${id}/deactivate`, { adminDeactivateProductReason: reason });
}

export const IMPORT_HEADER = "SKU,name,currentPrice,lowStockThreshold";
export interface ImportAnswer {
  rejected: { line: number; reason: string }[];
}

export async function importProducts(file: File): Promise<ActionResult<ImportAnswer>> {
  const body = new FormData();
  body.append("adminImportProductCSVFile", file);
  try {
    const answer = await sendJson<{
      adminImportProductCSVCreatedCount: number;
      adminImportProductCSVRejectedCount: number;
      adminImportProductCSVRejectedLineList: { adminImportProductCSVLineNumber: number; adminImportProductCSVRejectReason: string }[];
    }>("POST", `${PRODUCTS}/import`, body);
    const created = answer.adminImportProductCSVCreatedCount;
    const rejected = answer.adminImportProductCSVRejectedCount;
    const message = `${created} ${created === 1 ? "product" : "products"} added, ${rejected} ${rejected === 1 ? "line" : "lines"} rejected.`;
    return { ok: true, message, data: {
      rejected: answer.adminImportProductCSVRejectedLineList.map((line) => ({
        line: line.adminImportProductCSVLineNumber,
        reason: line.adminImportProductCSVRejectReason,
      })),
    } };
  } catch (error) {
    return failedAction(error);
  }
}
