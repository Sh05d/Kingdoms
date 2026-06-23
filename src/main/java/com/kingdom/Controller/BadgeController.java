package com.kingdom.Controller;

import com.kingdom.API.ApiResponse;
import com.kingdom.DTO.IN.BadgeIN;
import com.kingdom.Model.Badge;
import com.kingdom.Service.BadgeService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
//this page for admin only
@RestController
@RequestMapping("/api/v1/badge")
@RequiredArgsConstructor
public class BadgeController {
    private final BadgeService badgeService;

    @GetMapping("/get")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(badgeService.getAllBadges());
    }

    @PostMapping("/add/{kingdomId}")
    public ResponseEntity<?> add(@PathVariable Integer kingdomId, @RequestBody @Valid BadgeIN badge) {
        badgeService.addBadge(kingdomId, badge);
        return ResponseEntity.ok(new ApiResponse("Badge created successfully"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody @Valid BadgeIN badge) {
        badgeService.updateBadge(id, badge);
        return ResponseEntity.ok(new ApiResponse("Badge updated successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        badgeService.deleteBadge(id);
        return ResponseEntity.ok(new ApiResponse("Badge deleted successfully"));
    }

}
