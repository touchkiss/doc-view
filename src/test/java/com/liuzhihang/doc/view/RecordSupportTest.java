package com.liuzhihang.doc.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Manual verification test for JDK 16+ record support.
 * Right-click each record or the controller class → Doc View to verify.
 *
 * @author liuzhihang
 */
public class RecordSupportTest {

    /**
     * 6.1 — Simple record: right-click UserDto → Doc View
     * Expected: doc with fields "name" (String) and "age" (int)
     */
    record UserDto(String name, int age) {}

    /**
     * 6.4 — Record with @Schema annotations
     * Expected: descriptions appear in generated doc
     */
    record ProductDto(
            @Schema(description = "商品ID", required = true) Long id,
            @Schema(description = "商品名称") String productName,
            @Schema(description = "单价") Double price
    ) {}

    /**
     * 6.5 — Regular class with a record-typed field
     * Expected: record components expand as nested child rows under "address"
     */
    record AddressRecord(String street, String city, String zipCode) {}

    static class OrderDto {
        private Long orderId;
        private AddressRecord address;
        private String status;
    }

    /**
     * 6.2 / 6.3 / 6.5 — Spring controller using records as request/response
     * Right-click the class → Doc View
     * Expected: createUser shows UserDto components as request body fields
     *           getProduct shows ProductDto components as response body fields
     */
    // Simulated controller — real controller would have @RestController, @RequestMapping etc.
    static class UserController {

        // 6.2: record as @RequestBody
        public UserDto createUser(UserDto request) {
            return request;
        }

        // 6.3: record as return type
        public ProductDto getProduct(Long id) {
            return null;
        }

        // 6.5: List<Record> as return type
        public java.util.List<AddressRecord> getAddresses() {
            return null;
        }
    }

    public static void main(String[] args) {
        // Run this class in IntelliJ sandbox IDE via runIde,
        // then open this file and right-click the record classes to verify Doc View.
    }
}
