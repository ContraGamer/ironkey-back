package com.lionfinance.ironkey.api.dto.auth.response;

public record TotpSetupResponse(

        // Secret en base32 — mostrar al usuario para entrada manual si el QR falla
        String secret,

        // URI otpauth:// para apps compatibles
        String qrCodeUri,

        // Imagen QR en base64 PNG — lista para mostrar en el frontend
        String qrCodeImage
) {}
