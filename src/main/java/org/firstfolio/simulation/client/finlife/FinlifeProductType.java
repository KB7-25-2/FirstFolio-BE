package org.firstfolio.simulation.client.finlife;

/**
 * finlife에서 가져올 상품 종류와 대응 엔드포인트.
 */
public enum FinlifeProductType {

    DEPOSIT("depositProductsSearch"),
    SAVING("savingProductsSearch");

    private final String endpoint;

    FinlifeProductType(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
