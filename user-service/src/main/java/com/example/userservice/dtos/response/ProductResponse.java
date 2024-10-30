package com.example.userservice.dtos.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Set;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductResponse implements Serializable {
    Long productId;
    String name;
    String description;
    String price;
    Integer stockQuantity;
    Integer reservedQuantity;
    Integer soldQuantity;
    String manufacturer;
    String size;
    String weight;
    Set<ProductImageResponse> images;
}
