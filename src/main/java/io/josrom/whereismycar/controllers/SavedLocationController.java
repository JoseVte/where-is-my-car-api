package io.josrom.whereismycar.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.josrom.whereismycar.dto.OnCreate;
import io.josrom.whereismycar.dto.OnUpdate;
import io.josrom.whereismycar.dto.SavedLocationRequest;
import io.josrom.whereismycar.dto.SavedLocationResponse;
import io.josrom.whereismycar.service.SavedLocationService;

@RestController
@RequestMapping("/api/saved-locations")
public class SavedLocationController {
    private final SavedLocationService savedLocationService;

    public SavedLocationController(SavedLocationService savedLocationService) {
        this.savedLocationService = savedLocationService;
    }

    @GetMapping("")
    public List<SavedLocationResponse> getAllSavedLocations(@RequestParam(required = false) Integer categoryId,
                                                            @AuthenticationPrincipal Jwt jwt) {
        return savedLocationService.findAll(jwt.getSubject(), categoryId);
    }

    @GetMapping("{id}")
    public SavedLocationResponse getSavedLocation(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return savedLocationService.find(id, jwt.getSubject());
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedLocationResponse createSavedLocation(@Validated(OnCreate.class) @RequestBody SavedLocationRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return savedLocationService.create(request, jwt.getSubject());
    }

    @PutMapping("{id}")
    public SavedLocationResponse updateSavedLocation(@PathVariable Integer id,
                                                     @Validated(OnUpdate.class) @RequestBody SavedLocationRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return savedLocationService.update(id, request, jwt.getSubject());
    }

    @DeleteMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSavedLocation(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        savedLocationService.delete(id, jwt.getSubject());
    }
}
