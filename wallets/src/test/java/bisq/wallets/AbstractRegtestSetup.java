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

package bisq.wallets;

import bisq.common.util.FileUtils;
import bisq.wallets.process.BisqProcess;
import bisq.wallets.rpc.RpcConfig;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;

public abstract class AbstractRegtestSetup<T extends BisqProcess, W> implements BisqProcess {
    public static final String WALLET_PASSPHRASE = "My super secret passphrase that nobody can guess.";

    protected T daemonProcess;
    protected final Path tmpDirPath;

    public AbstractRegtestSetup() throws IOException {
        this.tmpDirPath = FileUtils.createTempDir();
    }

    protected abstract T createProcess() throws IOException;

    public void start() throws IOException {
        daemonProcess = createProcess();
        daemonProcess.start();
    }

    public void shutdown() {
        daemonProcess.shutdown();
    }

    public abstract void mineOneBlock();

    public abstract W createNewWallet(Path walletPath) throws MalformedURLException;

    public abstract void fundWallet(W receiverWallet, double amount);

    public abstract RpcConfig getRpcConfig();
}
