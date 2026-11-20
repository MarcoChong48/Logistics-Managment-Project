package com.lms.backend.admin.product;

import static com.lms.backend.admin.product.AdminProductRule.ADMIN_PRODUCT_QR_CODE_IMAGE_SIZE;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lms.backend.shared.database.ProductRepository;

@Service
public class AdminProductQRCodeService {

    // ===== SECTION 1: OTHER CLASSES THIS CLASS USES =====

    private final ProductRepository adminViewProductQRCodeRepository;

    /**
     * What it does: Connects the QR encoder to the current product catalogue.
     * Input: The product repository.
     * Output: A product label service.
     */
    public AdminProductQRCodeService(ProductRepository adminViewProductQRCodeRepository) {
        this.adminViewProductQRCodeRepository = adminViewProductQRCodeRepository;
    }

    // ===== SECTION 2: adminViewProductQRCode =====

    /**
     * What it does: Encodes the saved SKU verbatim into a 300 by 300 PNG.
     * Input: The product ID.
     * Output: PNG bytes, or 404 when the product cannot be found.
     * Placeholder: PO-09. Warehouse compares the scanned value to the SKU when connected.
     */
    @Transactional(readOnly = true)
    public byte[] adminViewProductQRCode(Long productID) {
        String SKU = adminViewProductQRCodeRepository.findById(productID)
                .orElseThrow(AdminProductService::adminRefuseProductNotFound).SKU;
        try {
            // PLACEHOLDER OUTPUT PO-09.
            // To: Warehouse.
            // What: The QR code holds only the product's stored SKU.
            // How to use: Warehouse compares the scanned text to the SKU of an order item when integrated.
            BitMatrix adminViewProductQRCodeMatrix = new QRCodeWriter()
                    .encode(SKU, BarcodeFormat.QR_CODE, ADMIN_PRODUCT_QR_CODE_IMAGE_SIZE,
                            ADMIN_PRODUCT_QR_CODE_IMAGE_SIZE);
            BufferedImage adminViewProductQRCodeImage = new BufferedImage(ADMIN_PRODUCT_QR_CODE_IMAGE_SIZE,
                    ADMIN_PRODUCT_QR_CODE_IMAGE_SIZE, BufferedImage.TYPE_BYTE_BINARY);
            for (int adminViewProductQRCodeRowNumber = 0;
                    adminViewProductQRCodeRowNumber < ADMIN_PRODUCT_QR_CODE_IMAGE_SIZE;
                    adminViewProductQRCodeRowNumber++) {
                for (int adminViewProductQRCodeColumnNumber = 0;
                        adminViewProductQRCodeColumnNumber < ADMIN_PRODUCT_QR_CODE_IMAGE_SIZE;
                        adminViewProductQRCodeColumnNumber++) {
                    adminViewProductQRCodeImage.setRGB(adminViewProductQRCodeColumnNumber,
                            adminViewProductQRCodeRowNumber, adminViewProductQRCodeMatrix.get(
                                    adminViewProductQRCodeColumnNumber, adminViewProductQRCodeRowNumber)
                                    ? 0xff000000 : 0xffffffff);
                }
            }
            ByteArrayOutputStream adminViewProductQRCodeBytes = new ByteArrayOutputStream();
            if (!ImageIO.write(adminViewProductQRCodeImage, "png", adminViewProductQRCodeBytes)) {
                throw new IllegalStateException("The PNG writer is missing.");
            }
            return adminViewProductQRCodeBytes.toByteArray();
        } catch (WriterException | IOException adminViewProductQRCodeError) {
            throw new IllegalStateException("The product QR code could not be generated.", adminViewProductQRCodeError);
        }
    }
}
