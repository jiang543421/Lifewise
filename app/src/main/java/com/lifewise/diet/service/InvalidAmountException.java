package com.lifewise.diet.service;

/** amount_g 必须为正数（V7 BR-12）。 */
public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(String message) {
        super(message);
    }
}