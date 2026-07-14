package com.taxi.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Typed, deliberately minimal contract for Click SuperApp GetInfo. */
public class ClickGetInfoRequest {
    private Integer action;

    @JsonProperty("service_id")
    private String serviceId;

    private Params params;

    public Integer getAction() { return action; }
    public void setAction(Integer action) { this.action = action; }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public Params getParams() { return params; }
    public void setParams(Params params) { this.params = params; }

    public static class Params {
        private String account;

        public String getAccount() { return account; }
        public void setAccount(String account) { this.account = account; }
    }
}
