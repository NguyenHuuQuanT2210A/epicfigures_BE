package com.example.productservice.services;

import com.example.productservice.dto.CategoryDTO;
import com.example.productservice.dto.ProductDTO;
import com.example.productservice.dto.ProductImageDTO;
import com.example.productservice.entities.Category;
import com.example.productservice.entities.Product;
import com.example.productservice.exception.CategoryNotFoundException;
import com.example.productservice.exception.CustomException;
import com.example.productservice.exception.NotFoundException;
import com.example.productservice.mapper.CategoryMapper;
import com.example.productservice.mapper.ProductMapper;
import com.example.productservice.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductImageService productImageService;

    @Override
    public int countProducts() {
        return (int) productRepository.count();
    }

    @Override
    public Page<ProductDTO> getAllProducts(Pageable pageable) {
        Page<Product> products = productRepository.findByDeletedAtIsNull(pageable);
        return products.map(productMapper.INSTANCE::productToProductDTO);
    }

    @Override
    public ProductDTO getProductByName(String name) {
        Product product = productRepository.findByNameAndDeletedAtIsNull(name);
        if (product == null) {
            throw new NotFoundException("Product not found with name: " + name);
        }
        return productMapper.INSTANCE.productToProductDTO(product);
    }

    @Override
    public ProductDTO getProductById(Long id) {
        Product product = findProductById(id);
        return productMapper.INSTANCE.productToProductDTO(product);
    }

    @Override
    public List<ProductDTO> getProductsByIds(Set<Long> productIds) {
        List<Product> products = productRepository.findByProductIdIn(productIds);
        products.forEach(product -> {
            if (product.getProductId() == null) {
                throw new CustomException("Product is not found", HttpStatus.NOT_FOUND);
            }
        });
        return productMapper.INSTANCE.productListToProductDTOList(products);
    }

    @Override
    public void addProduct(ProductDTO productDTO, List<MultipartFile> imageFiles) {
        if (productRepository.existsByName(productDTO.getName())) {
            throw new CustomException("Product already exists with name: " + productDTO.getName(), HttpStatus.CONFLICT);
        }

        CategoryDTO categoryDTO = categoryService.getCategoryById(productDTO.getCategoryId());
        if (categoryDTO == null) {
            throw new CustomException("Can not find category with id " + productDTO.getCategoryId(), HttpStatus.NOT_FOUND);
        }


        Product product = productMapper.INSTANCE.productDTOToProduct(productDTO);

        product.setCategory(categoryMapper.INSTANCE.categoryDTOToCategory(categoryDTO));

        productRepository.save(product);

        productImageService.saveProductImage(product.getProductId(), imageFiles);
    }

    @Override
    public Page<ProductDTO> findByCategory(Pageable pageable, CategoryDTO categoryDTO) {
        CategoryDTO category = categoryService.getCategoryById(categoryDTO.getCategoryId());
        if (category == null) {
            throw new NotFoundException("Can not find category with id " + categoryDTO.getCategoryId());
        }
        return productRepository.findByCategoryAndDeletedAtIsNull(pageable, categoryMapper.INSTANCE.categoryDTOToCategory(category))
                .map(productMapper.INSTANCE::productToProductDTO);
    }

    @Override
    public void updateProduct(long id, ProductDTO updatedProductDTO, List<MultipartFile> imageFiles) {
        Optional<Product> existingProduct = productRepository.findById(id);
        if (existingProduct.isEmpty()) {
            throw new NotFoundException("Can not find product with id" + id);
        }
        if (updatedProductDTO.getPrice() != null) {
            existingProduct.get().setPrice(updatedProductDTO.getPrice());
        }
        if (updatedProductDTO.getName() != null) {
            if (productRepository.existsByName(updatedProductDTO.getName()) && !updatedProductDTO.getProductId().equals(existingProduct.get().getProductId())) {
                throw new CustomException("Product already exists with name: " + updatedProductDTO.getName(), HttpStatus.BAD_REQUEST);
            }
            existingProduct.get().setName(updatedProductDTO.getName());
        }
        if (updatedProductDTO.getDescription() != null) {
            existingProduct.get().setDescription(updatedProductDTO.getDescription());
        }

//        if (updatedProductDTO.getImages() != null) {
//            Set<ProductImageDTO> images = updatedProductDTO.getImages();
//            existingProduct.get().setImages(productMapper.INSTANCE.productImageDTOSetToProductImageSet(images));
//        }

//   not do this     if (updatedProductDTO.getStockQuantity() != null) {
//            existingProduct.get().setStockQuantity(updatedProductDTO.getStockQuantity());
//        }

        if (updatedProductDTO.getManufacturer() != null) {
            existingProduct.get().setManufacturer(updatedProductDTO.getManufacturer());
        }

        if (updatedProductDTO.getSize() != null) {
            existingProduct.get().setSize(updatedProductDTO.getSize());
        }

        if (updatedProductDTO.getWeight() != null) {
            existingProduct.get().setWeight(updatedProductDTO.getWeight());
        }

        if (updatedProductDTO.getCategoryId() != null) {
            CategoryDTO categoryDTO = categoryService.getCategoryById(updatedProductDTO.getCategoryId());
            if (categoryDTO == null) {
                throw new CategoryNotFoundException("Can not find category with id " + updatedProductDTO.getCategoryId());
            }

            Category category = categoryMapper.INSTANCE.categoryDTOToCategory(categoryDTO);
            existingProduct.get().setCategory(category);
        }
        productRepository.save(existingProduct.get());

        List<Long> productImageIds = new ArrayList<>();
        List<ProductImageDTO> productImageDTOs = productImageService.getProductImages(existingProduct.get().getProductId());
        for (ProductImageDTO productImageDTO : productImageDTOs) {
            productImageIds.add(productImageDTO.getImageId());
        }
        productImageService.updateProductImage(existingProduct.get().getProductId(), productImageIds , imageFiles);

    }

    @Override
    public void updateStockQuantity(long id, Integer stockQuantity) {
        Product product = findProductById(id);
        product.setStockQuantity(stockQuantity);
        productRepository.save(product);
    }

    @Override
    public void deleteProduct(long id) {
        findProductById(id);

        productRepository.deleteById(id);
    }

    @Override
    public void moveToTrash(Long id) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            throw new NotFoundException("Cannot find this product id: " + id);
        }
        LocalDateTime now = LocalDateTime.now();
        product.setDeletedAt(now);
        productRepository.save(product);
    }

    @Override
    public Page<ProductDTO> getInTrash(Pageable pageable) {
        Page<Product> products = productRepository.findByDeletedAtIsNotNull(pageable);
        return products.map(productMapper.INSTANCE::productToProductDTO);
    }

    private Product findProductById(long id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product not found with id: " + id));
    }
}