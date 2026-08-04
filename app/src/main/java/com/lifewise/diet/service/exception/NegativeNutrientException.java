package com.lifewise.diet.service.exception;

/** 营养字段为负（BR-13）。 */
public class NegativeNutrientException extends RuntimeException {
    public NegativeNutrientException(String field) {
        super("nutrient must be >= 0: " + field);
    }
}