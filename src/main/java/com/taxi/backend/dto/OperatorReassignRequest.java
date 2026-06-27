package com.taxi.backend.dto;

/** Operator buyurtmani boshqa haydovchiga (driverCode bo'yicha) berish so'rovi. */
public class OperatorReassignRequest {
    private String driverCode;

    public String getDriverCode() { return driverCode; }
    public void setDriverCode(String driverCode) { this.driverCode = driverCode; }
}
