package com.forcegym.media.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
public class MediaController {

  @GetMapping("/assets")
  public List<MediaAssetResponse> assets() {
    return List.of(
        new MediaAssetResponse(
            "media-demo-001",
            "member-demo-001",
            "progress-photo",
            "PENDING_UPLOAD"));
  }

  public record MediaAssetResponse(String id, String memberId, String type, String status) {}
}