package com.lms.backend.client.product;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.lms.backend.shared.database.AppUser;
import com.lms.backend.shared.database.AppUserRepository;
import com.lms.backend.shared.database.Inventory;
import com.lms.backend.shared.database.InventoryRepository;
import com.lms.backend.shared.database.Product;
import com.lms.backend.shared.database.ProductRepository;
import com.lms.backend.shared.log.SharedSecurityLogWriter;
import com.lms.backend.shared.value.SharedAccountStatus;
import com.lms.backend.shared.value.SharedUserRole;

@SpringBootTest(properties = {
        "lms.shared.bootstrap-admin-username=",
        "lms.shared.bootstrap-admin-temporary-password="
})
@AutoConfigureMockMvc
@Transactional
class ClientProductApiTest {

    private static final String PRODUCTS = "/api/client/products";
    private static final String PASSWORD = "CatalogTest123!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProductRepository products;

    @Autowired
    private InventoryRepository inventory;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Security log writes commit separately in production. Keep test fixtures in the rollback transaction.
    @MockitoBean
    private SharedSecurityLogWriter securityLogWriter;

    @Test
    void listsActiveProductsWithPriceAndNetAvailableStock() throws Exception {
        String catalog = "Trail-" + UUID.randomUUID();
        Product active = createProduct(catalog + " Boots", "ACTIVE");
        createInventory(active, 11, 4);
        Product inactive = createProduct(catalog + " Coat", "INACTIVE");
        createInventory(inactive, 12, 0);

        mvc.perform(get(PRODUCTS).servletPath(PRODUCTS)
                        .session(signIn(SharedUserRole.CLIENT))
                        .queryParam("name", catalog))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].length()").value(5))
                .andExpect(jsonPath("$[0].productId").value(active.productId))
                .andExpect(jsonPath("$[0].sku").value(active.sku))
                .andExpect(jsonPath("$[0].name").value(active.name))
                .andExpect(jsonPath("$[0].currentPriceHkd").value(79.50))
                .andExpect(jsonPath("$[0].availableStock").value(7));
    }

    @ParameterizedTest
    @CsvSource({"7,7", "3,7", "0,0"})
    void reportsZeroStockWhenNoneIsAvailable(int physicalStock, int reservedStock) throws Exception {
        Product product = createProduct("Tent-" + UUID.randomUUID(), "ACTIVE");
        createInventory(product, physicalStock, reservedStock);

        mvc.perform(get(PRODUCTS).servletPath(PRODUCTS)
                        .session(signIn(SharedUserRole.CLIENT))
                        .queryParam("name", product.name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availableStock").value(0));
    }

    @Test
    void listsProductsWithoutSearchAndReportsZeroForMissingInventory() throws Exception {
        Product product = createProduct("Pack-" + UUID.randomUUID(), "ACTIVE");

        mvc.perform(get(PRODUCTS).servletPath(PRODUCTS).session(signIn(SharedUserRole.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sku == '%s')].availableStock".formatted(product.sku)).value(
                        org.hamcrest.Matchers.contains(0)));
    }

    @Test
    void searchesByCaseInsensitiveLiteralNameSubstring() throws Exception {
        String catalog = UUID.randomUUID().toString();
        Product match = createProduct(catalog + " Trek%Pole", "ACTIVE");
        createProduct(catalog + " TrekX Pole", "ACTIVE");

        mvc.perform(get(PRODUCTS).servletPath(PRODUCTS)
                        .session(signIn(SharedUserRole.CLIENT))
                        .queryParam("name", "  " + catalog + " TREK%  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sku").value(match.sku));
    }

    @Test
    void rejectsAnonymousAccess() throws Exception {
        mvc.perform(get(PRODUCTS).servletPath(PRODUCTS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SHARED_NOT_LOGGED_IN"));
    }

    @ParameterizedTest
    @EnumSource(names = {"ADMIN", "WAREHOUSE_WORKER", "DELIVERY_WORKER"})
    void rejectsOtherSignedInRoles(SharedUserRole role) throws Exception {
        mvc.perform(get(PRODUCTS).servletPath(PRODUCTS).session(signIn(role)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SHARED_ACCESS_DENIED"));
    }

    @Test
    void preservesAdminUserEndpointPermissions() throws Exception {
        String adminUsers = "/api/admin/users";

        mvc.perform(get(adminUsers).servletPath(adminUsers).session(signIn(SharedUserRole.ADMIN)))
                .andExpect(status().isOk());
        mvc.perform(get(adminUsers).servletPath(adminUsers).session(signIn(SharedUserRole.CLIENT)))
                .andExpect(status().isForbidden());
    }

    private Product createProduct(String name, String status) {
        Product product = new Product();
        product.sku = UUID.randomUUID().toString();
        product.name = name;
        product.currentPrice = new BigDecimal("79.50");
        product.listingStatus = status;
        return products.saveAndFlush(product);
    }

    private void createInventory(Product product, int physicalStock, int reservedStock) {
        Inventory stock = new Inventory();
        stock.productId = product.productId;
        stock.physicalStock = physicalStock;
        stock.reservedStock = reservedStock;
        inventory.saveAndFlush(stock);
    }

    private MockHttpSession signIn(SharedUserRole role) throws Exception {
        AppUser account = new AppUser();
        account.username = "catalog-" + UUID.randomUUID();
        account.passwordHash = passwordEncoder.encode(PASSWORD);
        account.role = role;
        account.accountStatus = SharedAccountStatus.ACTIVE;
        users.saveAndFlush(account);

        String sessionPath = "/api/shared/session";
        var guest = mvc.perform(get(sessionPath).servletPath(sessionPath))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession guestSession = assertInstanceOf(MockHttpSession.class, guest.getRequest().getSession(false));
        var sessionInfo = JsonPath.parse(guest.getResponse().getContentAsString());
        String csrfHeader = sessionInfo.read("$.sharedViewSessionSecurityTokenHeaderName");
        String csrfToken = sessionInfo.read("$.sharedViewSessionSecurityToken");

        String loginPath = "/api/shared/login";
        var signedIn = mvc.perform(post(loginPath).servletPath(loginPath)
                        .session(guestSession)
                        .header(csrfHeader, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sharedLoginUserUsername":"%s","sharedLoginUserPassword":"%s"}
                                """.formatted(account.username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return assertInstanceOf(MockHttpSession.class, signedIn.getRequest().getSession(false));
    }
}
