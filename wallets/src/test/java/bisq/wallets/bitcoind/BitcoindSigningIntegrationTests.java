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

package bisq.wallets.bitcoind;

import bisq.wallets.AddressType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BitcoindSigningIntegrationTests extends SharedBitcoindInstanceTests {
    private static final String MESSAGE = "my message";

    @Test
    public void signAndVerifyMessage() {
        String address = minerWallet.getNewAddress(AddressType.LEGACY, "");
        String signature = minerWallet.signMessage(address, MESSAGE);
        boolean isValid = minerWallet.verifyMessage(address, signature, MESSAGE);
        assertTrue(isValid);
    }
}
