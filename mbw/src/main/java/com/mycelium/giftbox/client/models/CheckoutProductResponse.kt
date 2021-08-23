/**
 * Gift Cards Shop API
 * Products catalog, checkout and orders API - allows realtime purchase of products
 *
 * The version of the OpenAPI document: 1.0
 *
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
package com.mycelium.giftbox.client.models

import android.os.Parcelable
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.android.parcel.Parcelize

/**
 * Product info, list of available currencies, quantity, amount and selected currency
 * @param currencies List of accepted currencies
 * @param fromCurrencyId Currency of price offer. By default BTC.
 * @param priceOffer Price of selected amount and quantity in given currency_id
 * @param product
 */
@Parcelize

data class CheckoutProductResponse(
    /* List of accepted currencies */
    @JsonProperty("currencies")
    var currencies: Array<CurrencyInfo>? = null,
    /* Currency of price offer. By default BTC. */
    @JsonProperty("fromCurrencyId")
    var fromCurrencyId: kotlin.String? = null,
    /* Price of selected amount and quantity in given currency_id */
    @JsonProperty("priceOffer")
    var priceOffer: kotlin.String? = null,
    @JsonProperty("product")
    var product: ProductInfo? = null
) : Parcelable
