package io.josrom.whereismycar.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.josrom.whereismycar.dto.CategoryRequest;
import io.josrom.whereismycar.entity.Category;
import io.josrom.whereismycar.service.CategoryService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("")
    public List<Category> getAllCategories(@AuthenticationPrincipal Jwt jwt) {
        return categoryService.findAll(jwt.getSubject());
    }

    @GetMapping("{id}")
    public Category getCategory(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return categoryService.find(id, jwt.getSubject());
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public Category createCategory(@Valid @RequestBody CategoryRequest request, @AuthenticationPrincipal Jwt jwt) {
        return categoryService.create(request, jwt.getSubject());
    }

    @PutMapping("{id}")
    public Category updateCategory(@PathVariable Integer id, @Valid @RequestBody CategoryRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return categoryService.update(id, request, jwt.getSubject());
    }

    @DeleteMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        categoryService.delete(id, jwt.getSubject());
    }
}
