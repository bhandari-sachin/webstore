package com.github.bhandarisachin.webstore.service;

import com.github.bhandarisachin.webstore.entity.ProductCategory;
import com.github.bhandarisachin.webstore.repository.ProductCategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductCategoryService {

    private final ProductCategoryRepository repository;

    public ProductCategoryService(
            ProductCategoryRepository repository) {
        this.repository = repository;
    }

    public List<ProductCategory> getAllCategories() {
        return repository.findAll();
    }
    public Optional<ProductCategory> getCategoryById(Integer id) {
        return repository.findById(id);
    }

    public ProductCategory createCategory(ProductCategory category) {
        return repository.save(category);
    }

    public Optional<ProductCategory> updateCategory(
            Integer id, ProductCategory updatedCategory) {

        return repository.findById(id).map(existing -> {
            existing.setName(updatedCategory.getName());
            existing.setDescription(updatedCategory.getDescription());
            return repository.save(existing);
        });
    }


    public boolean deleteCategory(Integer id) {
        if (!repository.existsById(id)) {
            return false;
        }

        repository.deleteById(id);
        return true;
    }

}