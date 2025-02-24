package com.example.userservice.services.impl;

import com.example.userservice.dtos.response.CartResponse;
import com.example.userservice.dtos.response.ProductImageResponse;
import com.example.userservice.entities.UserAndProductId;
import com.example.userservice.entities.Cart;
import com.example.userservice.exceptions.AppException;
import com.example.userservice.exceptions.CustomException;
import com.example.userservice.exceptions.ErrorCode;
import com.example.userservice.repositories.CartRepository;
import com.example.userservice.services.CartService;
import com.example.userservice.services.ProductClient;
import com.example.userservice.statics.enums.CartStatus;
import com.example.userservice.util.ParseBigDecimal;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartServiceImpl implements CartService {
    CartRepository cartRepository;
    ProductClient productClient;

    @Override
    public List<Cart> getAllCart() {
        return cartRepository.findAll().stream().toList();
    }

    @Override
    public Cart getCartById(UserAndProductId ids) {
        return cartRepository.findCartById(ids);
    }

    @Override
    public List<CartResponse> getCartByUserId(Long userId) {
        var carts = cartRepository.findAllByUserId(userId);
        List<Cart> cartsToCheck = new ArrayList<>();
        for (var cart : carts) {
            var product = productClient.getProductById(cart.getId().getProductId()).getData();
            if (product == null) {
                cart.setStatus(CartStatus.DISCONTINUED);
            }else if (product.getStockQuantity() == 0) {
                cart.setStatus(CartStatus.OUT_OF_STOCK);
            }else if (product.getStockQuantity() < cart.getQuantity()) {
                cart.setStatus(CartStatus.EXCEEDED_AVAILABLE_STOCK);
            }else if (product.getPrice().compareTo(cart.getTotalPrice().divide(BigDecimal.valueOf(cart.getQuantity()), 2, RoundingMode.HALF_UP)) != 0) {
                cart.setStatus(CartStatus.PRICE_CHANGED);
            }else {
                cart.setStatus(CartStatus.AVAILABLE);
            }
            cartRepository.save(cart);
            cartsToCheck.add(cart);
        }
        return getCartResponse(cartsToCheck);
    }

    @Override
    public List<CartResponse> getCartByProductId(Long productId) {
        var carts = cartRepository.findAllByProductId(productId);
        return getCartResponse(carts);
    }

    @Override
    public Cart addCart(Cart cart) {
        var cartExist = getCartById(cart.getId());
        var product = productClient.getProductById(cart.getId().getProductId()).getData();

        if ((product.getStockQuantity() - product.getReservedQuantity()) < cart.getQuantity() || product.getStockQuantity() == 0)
        {
            throw new AppException(ErrorCode.EXCEED_PRODUCT_QUANTITY);
        }

        if (cartExist == null) {
            return cartRepository.save(Cart.builder().id(cart.getId())
                    .quantity(cart.getQuantity())
                    .unitPrice(product.getPrice())
                    .totalPrice(BigDecimal.valueOf(cart.getQuantity()).multiply(product.getPrice()))
                    .status(CartStatus.AVAILABLE)
                    .build());

        }else {
            if ((product.getStockQuantity() - product.getReservedQuantity()) < cart.getQuantity() + cartExist.getQuantity())
            {
                throw new AppException(ErrorCode.EXCEED_PRODUCT_QUANTITY);
            }
            cartExist.setQuantity(cart.getQuantity() + cartExist.getQuantity());
            cartExist.setUnitPrice(product.getPrice());
            cartExist.setTotalPrice(BigDecimal.valueOf(cartExist.getQuantity()).multiply(product.getPrice()));
            return cartRepository.save(cartExist);
        }
    }

    @Override
    public Cart updateQuantity(UserAndProductId ids, Integer quantity) {
        Cart cart = getCartById(ids);
        if (cart == null) {
            throw new CustomException("Cart not found", HttpStatus.NOT_FOUND);
        }
        var product = productClient.getProductById(cart.getId().getProductId()).getData();
        if ((product.getStockQuantity() - product.getReservedQuantity())  < quantity || product.getStockQuantity() == 0)
        {
            throw new AppException(ErrorCode.EXCEED_PRODUCT_QUANTITY);
        }
        cart.setQuantity(quantity);
        cart.setUnitPrice(product.getPrice());
        cart.setTotalPrice(BigDecimal.valueOf(quantity).multiply(product.getPrice()));
        return cartRepository.save(cart);
    }

    @Override
    public void deleteCart(UserAndProductId id) {
        cartRepository.deleteById(id);
    }

    @Override
    public void deleteCarts(List<UserAndProductId> ids) {
        for (UserAndProductId id : ids) {
            deleteCart(id);
        }
    }

    @Override
    @Transactional
    public void deleteCartByUserId(Long userId) {
        cartRepository.deleteCartsByUserId(userId);
    }

    private List<CartResponse> getCartResponse(List<Cart> carts){
        List<CartResponse> cartResponses = new ArrayList<>();

        for (var cart : carts) {
            Set<String> productImagesUrl = new HashSet<>();
            var product = productClient.getProductById(cart.getId().getProductId()).getData();
            for (ProductImageResponse productImage : product.getImages()) {
                productImagesUrl.add(productImage.getImageUrl());
            }
            cartResponses.add(CartResponse.builder()
                    .id(cart.getId())
                    .quantity(cart.getQuantity())
                    .productName(product.getName())
                    .productPrice(product.getPrice())
                    .description(product.getDescription())
                    .status(cart.getStatus())
                    .unitPrice(cart.getUnitPrice())
                    .totalPrice(cart.getTotalPrice())
                    .productImages(productImagesUrl)
                    .build());
        }
        return cartResponses;
    }

}
