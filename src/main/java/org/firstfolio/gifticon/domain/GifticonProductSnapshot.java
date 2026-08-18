package org.firstfolio.gifticon.domain;

public record GifticonProductSnapshot(
        long gifticonProductId,
        String name,
        String brandName,
        String category,
        int faceValueKrw,
        int requiredPoints,
        String imageUrl
) { }
