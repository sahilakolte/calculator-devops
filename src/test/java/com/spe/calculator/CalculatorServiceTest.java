package com.spe.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CalculatorServiceTest {

    private final CalculatorService service = new CalculatorService();

    @Test
    void addsTwoNumbers() {
        assertEquals(5, service.add(2, 3));
        assertEquals(-1.5, service.add(-3, 1.5));
    }

    @Test
    void subtractsTwoNumbers() {
        assertEquals(-1, service.subtract(2, 3));
        assertEquals(10, service.subtract(7, -3));
    }

    @Test
    void multipliesTwoNumbers() {
        assertEquals(6, service.multiply(2, 3));
        assertEquals(0, service.multiply(0, 42));
        assertEquals(-7.5, service.multiply(2.5, -3));
    }

    @Test
    void dividesTwoNumbers() {
        assertEquals(2.5, service.divide(5, 2));
        assertEquals(-4, service.divide(8, -2));
    }

    @Test
    void divideByZeroThrows() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.divide(1, 0));
        assertEquals("Division by zero is not allowed", ex.getMessage());
    }

    @Test
    void overflowThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.multiply(Double.MAX_VALUE, 10));
    }
}
