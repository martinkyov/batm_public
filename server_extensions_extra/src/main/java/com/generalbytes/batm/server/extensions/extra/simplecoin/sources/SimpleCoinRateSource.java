/*************************************************************************************
 * Copyright (C) 2014-2020 GENERAL BYTES s.r.o. All rights reserved.
 *
 * This software may be distributed and modified under the terms of the GNU
 * General Public License version 2 (GPL2) as published by the Free Software
 * Foundation and appearing in the file GPL2.TXT included in the packaging of
 * this file. Please note that GPL2 Section 2[b] requires that all works based
 * on this software must also be made publicly available under the terms of
 * the GPL2 ("Copyleft").
 *
 * Contact information
 * -------------------
 *
 * GENERAL BYTES s.r.o.
 * Web      :  http://www.generalbytes.com
 *
 ************************************************************************************/

package com.generalbytes.batm.server.extensions.extra.simplecoin.sources;

import com.generalbytes.batm.common.currencies.CryptoCurrency;
import com.generalbytes.batm.common.currencies.FiatCurrency;
import com.generalbytes.batm.server.extensions.IRateSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.mazi.rescu.RestProxyFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class SimpleCoinRateSource implements IRateSource {

    private static final Logger log = LoggerFactory.getLogger(SimpleCoinRateSource.class);


    private String preferedFiatCurrency = FiatCurrency.USD.getCode();
    private ISimpleCoinApi api;

    private static HashMap<String, BigDecimal> rateAmounts = new HashMap<String, BigDecimal>();
    private static HashMap<String, Long> rateTimes = new HashMap<String, Long>();
    private static final long MAXIMUM_ALLOWED_TIME_OFFSET = 30 * 1000; //30sec


    public SimpleCoinRateSource(String preferedFiatCurrency) {
        if (preferedFiatCurrency == null) {
            preferedFiatCurrency = FiatCurrency.USD.getCode();
        }
        this.preferedFiatCurrency = preferedFiatCurrency;
        api = RestProxyFactory.createProxy(ISimpleCoinApi.class, "https://server.simplecoin.eu");
    }

    @Override
    public Set<String> getFiatCurrencies() {
        Set<String> result = new HashSet<String>();
        result.add(FiatCurrency.CZK.getCode());
        result.add(FiatCurrency.USD.getCode());
        return result;
    }

    @Override
    public String getPreferredFiatCurrency() {
        return preferedFiatCurrency;
    }

    @Override
    public Set<String> getCryptoCurrencies() {
        Set<String> result = new HashSet<>();
        result.add(CryptoCurrency.BTC.getCode());
        return result;
    }

    @Override
    public synchronized BigDecimal getExchangeRateLast(String cryptoCurrency, String fiatCurrency) {
        String key = cryptoCurrency + "_" + fiatCurrency;
        if (!(CryptoCurrency.BTC.getCode().equalsIgnoreCase(cryptoCurrency))) {
            return null;
        }
        if (!(FiatCurrency.USD.getCode().equalsIgnoreCase(fiatCurrency) || FiatCurrency.CZK.getCode().equalsIgnoreCase(fiatCurrency)) || (FiatCurrency.EUR.getCode().equalsIgnoreCase(fiatCurrency))) {
            return null;
        }

        synchronized (rateAmounts) {
            long now = System.currentTimeMillis();
            BigDecimal amount = rateAmounts.get(key);
            if (amount == null) {
                BigDecimal result = getExchangeRateLastSync(cryptoCurrency, fiatCurrency);
//                log.debug("Called SimpleCoin exchange for rate: " + key + " = " + result);
                log.debug("Called SimpleCoin currency rate for: {} = {}", key, result);
                rateAmounts.put(key, result);
                rateTimes.put(key, now + MAXIMUM_ALLOWED_TIME_OFFSET);
                return result;
            } else {
                Long expirationTime = rateTimes.get(key);
                if (expirationTime > now) {
                    return rateAmounts.get(key);
                } else {
                    //do the job;
                    BigDecimal result = getExchangeRateLastSync(cryptoCurrency, fiatCurrency);
                    log.debug("Called simplecoin.eu exchange for rate: {} = {}", key , result);
                    rateAmounts.put(key, result);
                    rateTimes.put(key, now + MAXIMUM_ALLOWED_TIME_OFFSET);
                    return result;
                }
            }
        }
    }

    private BigDecimal getExchangeRateLastSync(String cryptoCurrency, String fiatCurrency) {
        if (!CryptoCurrency.BTC.getCode().equalsIgnoreCase(cryptoCurrency)) {
            return null; //unsupported currency
        }
        FiatCryptoResponse fiatCryptoResponse = api.returnRate(fiatCurrency, cryptoCurrency);

        if (fiatCryptoResponse != null && fiatCryptoResponse.getError() == null && "ok".equalsIgnoreCase(fiatCryptoResponse.getStatus())) {
            SimpleCoinResponse response = fiatCryptoResponse.getResponse();
            log.debug("Ocekavana Hodnota je: " + response.getRate());
            return response.getRate(); // Overit jestli neni null
        } else {
            if (fiatCryptoResponse.getError() != null) {
                log.debug("API SimpleCoin error: " + fiatCryptoResponse.getError());
            }
        }
        return null;
    }

}

