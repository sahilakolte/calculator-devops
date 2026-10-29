package com.spe.calculator;

import org.springframework.stereotype.Service;

/**
 * Pure arithmetic logic, kept separate from the web layer so it can be unit tested directly.
 */
@Service
public class CalculatorService {

    public double add(double a, double b) {
        return checkFinite(a + b);
    }

    public double subtract(double a, double b) {
        return checkFinite(a - b);
    }

    public double multiply(double a, double b) {
        return checkFinite(a * b);
    }

    public double divide(double a, double b) {
        if (b == 0) {
            throw new IllegalArgumentException("Division by zero is not allowed");
        }
        return checkFinite(a / b);
    }

    private double checkFinite(double result) {
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Result is out of range");
        }
        return result;
    }
}
