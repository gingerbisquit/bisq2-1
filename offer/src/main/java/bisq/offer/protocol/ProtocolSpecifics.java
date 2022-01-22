/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.offer.protocol;

import bisq.account.settlement.CryptoSettlement;
import bisq.account.settlement.FiatSettlement;
import bisq.common.monetary.QuoteCodePair;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ProtocolSpecifics {
    public static List<FiatSettlement.Method> getFiatSettlementMethods(SwapProtocolType protocolType) {
        return switch (protocolType) {
            case BTC_XMR_SWAP -> throw new IllegalArgumentException("No fiat support for that protocolType");
            case LIQUID_SWAP -> throw new IllegalArgumentException("No fiat support for that protocolType");
            case BSQ_SWAP -> throw new IllegalArgumentException("No fiat support for that protocolType");
            case LN_SWAP -> throw new IllegalArgumentException("No fiat support for that protocolType");
            case MULTISIG -> List.of(FiatSettlement.Method.values());
            case BSQ_BOND -> List.of(FiatSettlement.Method.values());
            case REPUTATION -> List.of(FiatSettlement.Method.values());
        };
    }

    public static List<CryptoSettlement.Method> getCryptoSettlementMethods(SwapProtocolType protocolType) {
        return switch (protocolType) {
            case BTC_XMR_SWAP -> List.of(CryptoSettlement.Method.MAINNET);
            case LIQUID_SWAP -> List.of(CryptoSettlement.Method.MAINNET);
            case BSQ_SWAP -> List.of(CryptoSettlement.Method.MAINNET);
            case LN_SWAP -> List.of(CryptoSettlement.Method.LN);
            case MULTISIG -> List.of(CryptoSettlement.Method.MAINNET);
            case BSQ_BOND -> List.of(CryptoSettlement.Method.values());
            case REPUTATION -> List.of(CryptoSettlement.Method.values());
        };
    }


    public static List<SwapProtocolType> getProtocols(QuoteCodePair quoteCodePair) {
        List<SwapProtocolType> result = new ArrayList<>();
        if (isBtcXmrSwapSupported(quoteCodePair)) {
            result.add(SwapProtocolType.BTC_XMR_SWAP);
        }
        if (isLiquidSwapSupported(quoteCodePair)) {
            result.add(SwapProtocolType.LIQUID_SWAP);
        }
        if (isBsqSwapSupported(quoteCodePair)) {
            result.add(SwapProtocolType.BSQ_SWAP);
        }
        if (isLNSwapSupported(quoteCodePair)) {
            result.add(SwapProtocolType.LN_SWAP);
        }
        if (isMultiSigSupported(quoteCodePair)) {
            result.add(SwapProtocolType.MULTISIG);
        }
        if (isBsqBondSupported(quoteCodePair)) {
            result.add(SwapProtocolType.BSQ_BOND);
        }
        if (isReputationSupported(quoteCodePair)) {
            result.add(SwapProtocolType.REPUTATION);
        }
        log.error("quoteCodePair={}, result={}", quoteCodePair, result);
        return result;
    }

    public static boolean isBtcXmrSwapSupported(QuoteCodePair quoteCodePair) {
        QuoteCodePair pair1 = new QuoteCodePair("BTC", "XMR");
        QuoteCodePair pair2 = new QuoteCodePair("XMR", "BTC");
        return quoteCodePair.equals(pair1) || quoteCodePair.equals(pair2);
    }

    public static boolean isLiquidSwapSupported(QuoteCodePair quoteCodePair) {
        return false;//todo need some liquid asset lookup table
    }

    public static boolean isBsqSwapSupported(QuoteCodePair quoteCodePair) {
        QuoteCodePair pair1 = new QuoteCodePair("BTC", "BSQ");
        QuoteCodePair pair2 = new QuoteCodePair("BSQ", "BTC");
        return quoteCodePair.equals(pair1) || quoteCodePair.equals(pair2);
    }

    public static boolean isLNSwapSupported(QuoteCodePair quoteCodePair) {
        return false;//todo need some liquid asset lookup table
    }

    public static boolean isMultiSigSupported(QuoteCodePair quoteCodePair) {
        return quoteCodePair.quoteCurrencyCode().equals("BTC") || quoteCodePair.baseCurrencyCode().equals("BTC");
    }

    public static boolean isBsqBondSupported(QuoteCodePair quoteCodePair) {
        return true;
    }

    public static boolean isReputationSupported(QuoteCodePair quoteCodePair) {
        return true;
    }
}