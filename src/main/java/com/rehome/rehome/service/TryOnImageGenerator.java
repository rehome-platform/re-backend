package com.rehome.rehome.service;

import com.rehome.rehome.entity.enums.TryOnModelType;

public interface TryOnImageGenerator {

    byte[] generateOutfit(TryOnModelType modelType, byte[] topImage, byte[] bottomImage);

    byte[] generateDress(TryOnModelType modelType, byte[] dressImage);
}