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

package bisq.application;

import bisq.account.AccountService;
import bisq.account.accountage.AccountAgeWitnessService;
import bisq.account.accounts.RevolutAccount;
import bisq.account.accounts.SepaAccount;
import bisq.common.locale.CountryRepository;
import bisq.common.locale.LocaleRepository;
import bisq.common.threading.ExecutorFactory;
import bisq.common.util.CompletableFutureUtils;
import bisq.i18n.Res;
import bisq.identity.IdentityService;
import bisq.network.NetworkService;
import bisq.network.NetworkServiceConfigFactory;
import bisq.offer.OfferBookService;
import bisq.offer.OfferService;
import bisq.offer.OpenOfferService;
import bisq.oracle.daobridge.DaoBridgeService;
import bisq.oracle.marketprice.MarketPriceService;
import bisq.oracle.marketprice.MarketPriceServiceConfigFactory;
import bisq.persistence.PersistenceService;
import bisq.protocol.ProtocolService;
import bisq.security.KeyPairService;
import bisq.security.SecurityService;
import bisq.settings.SettingsService;
import bisq.social.SocialService;
import bisq.social.chat.ChatService;
import bisq.social.intent.TradeIntentService;
import bisq.social.user.profile.UserProfileService;
import bisq.wallets.NetworkType;
import bisq.wallets.WalletBackend;
import bisq.wallets.WalletConfig;
import bisq.wallets.WalletService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static bisq.common.util.OsUtils.EXIT_FAILURE;
import static bisq.common.util.OsUtils.EXIT_SUCCESS;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.concurrent.CompletableFuture.runAsync;

/**
 * Creates domain specific options from program arguments and application options.
 * Creates domain instance with options and optional dependency to other domain objects.
 * Initializes the domain instances according to the requirements of their dependencies either in sequence
 * or in parallel.
 * Provides the completely setup instances to other clients (Api)
 */
@Getter
@Slf4j
public class DefaultApplicationService extends ServiceProvider {
    public static final ExecutorService DISPATCHER = ExecutorFactory.newSingleThreadExecutor("DefaultApplicationService.dispatcher");

    private final NetworkService networkService;
    private final OpenOfferService openOfferService;
    private final IdentityService identityService;
    private final MarketPriceService marketPriceService;
    private final ApplicationConfig applicationConfig;
    private final PersistenceService persistenceService;
    private final SettingsService settingsService;
    private final ChatService chatService;
    private final ProtocolService protocolService;
    private final OfferBookService offerBookService;
    private final AccountService accountService;
    private final TradeIntentService tradeIntentService;
    private final UserProfileService userProfileService;
    private final WalletService walletService;
    private final OfferService offerService;
    private final SocialService socialService;
    private final SecurityService securityService;
    private final DaoBridgeService daoBridgeService;
    private final AccountAgeWitnessService accountAgeWitnessService;

    public interface Listener {
        void onStateChanged(State state);
    }

    public enum State {
        CREATED,
        SECURITY_SERVICE_INITIALIZED,
        NETWORK_BOOTSTRAPPED,
        IDENTITY_SERVICE_INITIALIZED,
        DAO_BRIDGE_SERVICE_INITIALIZED,
        MARKET_PRICE_SERVICE_INITIALIZED,
        ACCOUNT_AGE_WITNESS_SERVICE_INITIALIZED,
        PROTOCOL_SERVICE_INITIALIZED,
        INIT_COMPLETE,
        INIT_FAILED
    }

    public AtomicReference<State> state = new AtomicReference<>(State.CREATED);
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    public DefaultApplicationService(String[] args) {
        super("Bisq");

        this.applicationConfig = ApplicationConfigFactory.getConfig(getConfig("bisq.application"), args);

        Locale locale = applicationConfig.getLocale();
        LocaleRepository.initialize(locale);
        Res.initialize(locale);

        persistenceService = new PersistenceService(applicationConfig.baseDir());

        securityService = new SecurityService(persistenceService);

        settingsService = new SettingsService(persistenceService);

        NetworkService.Config networkServiceConfig = NetworkServiceConfigFactory.getConfig(applicationConfig.baseDir(),
                getConfig("bisq.networkServiceConfig"));
        KeyPairService keyPairService = securityService.getKeyPairService();
        networkService = new NetworkService(networkServiceConfig, persistenceService, keyPairService);

        IdentityService.Config identityServiceConfig = IdentityService.Config.from(getConfig("bisq.identityServiceConfig"));
        identityService = new IdentityService(persistenceService, keyPairService, networkService, identityServiceConfig);

        accountService = new AccountService(persistenceService);
        accountAgeWitnessService = new AccountAgeWitnessService(networkService, identityService);

        socialService = new SocialService();
        UserProfileService.Config userProfileServiceConfig = UserProfileService.Config.from(getConfig("bisq.userProfileServiceConfig"));
        userProfileService = new UserProfileService(persistenceService, userProfileServiceConfig, keyPairService, identityService, networkService);
        chatService = new ChatService(persistenceService, identityService, networkService, userProfileService);
        tradeIntentService = new TradeIntentService(networkService, identityService, chatService, persistenceService);

        // add data use case is not available yet at networkService
        offerService = new OfferService();
        openOfferService = new OpenOfferService(networkService, identityService, persistenceService);
        offerBookService = new OfferBookService(networkService);

        MarketPriceService.Config marketPriceServiceConf = MarketPriceServiceConfigFactory.getConfig();
        marketPriceService = new MarketPriceService(marketPriceServiceConf, networkService, ApplicationVersion.VERSION);
        // offerPresentationService = new OfferPresentationService(offerService, marketPriceService);

        protocolService = new ProtocolService(networkService, identityService, persistenceService, openOfferService);

        Optional<WalletConfig> walletConfig = !isRegtestRun() ? Optional.empty() : createRegtestWalletConfig();
        walletService = new WalletService(walletConfig);

        daoBridgeService = new DaoBridgeService(networkService, identityService, getConfig("bisq.oracle.daoBridge"));
    }

    public CompletableFuture<Boolean> readAllPersisted() {
        return persistenceService.readAllPersisted();
    }

    /**
     * Initializes all domain objects, services and repositories.
     * We do in parallel as far as possible. If there are dependencies we chain those as sequence.
     */
    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.completedFuture(true)
                .thenCompose(result -> setStateAfter(securityService.initialize(), State.SECURITY_SERVICE_INITIALIZED))
                .thenCompose(result -> setStateAfter(networkService.bootstrapToNetwork(), State.NETWORK_BOOTSTRAPPED))
                .thenCompose(result -> setStateAfter(identityService.initialize(), State.IDENTITY_SERVICE_INITIALIZED))
                .thenCompose(result -> setStateAfter(daoBridgeService.initialize(), State.DAO_BRIDGE_SERVICE_INITIALIZED))
                .thenCompose(result -> setStateAfter(marketPriceService.initialize(), State.MARKET_PRICE_SERVICE_INITIALIZED))
                .whenComplete((list, throwable) -> {
                    log.info("add dummy accounts");
                    if (accountService.getAccounts().isEmpty()) {
                        SepaAccount john_smith = new SepaAccount("SEPA-account-1",
                                "John Smith",
                                "iban_1234",
                                "bic_1234",
                                CountryRepository.getDefaultCountry());
                        accountService.addAccount(john_smith);
                        accountService.addAccount(new SepaAccount("SEPA-account-2",
                                "Mary Smith",
                                "iban_5678",
                                "bic_5678",
                                CountryRepository.getDefaultCountry()));
                        accountService.addAccount(new RevolutAccount("revolut-account", "john@gmail.com"));
                    }
                })
                .thenCompose(result -> setStateAfter(accountAgeWitnessService.initialize(), State.ACCOUNT_AGE_WITNESS_SERVICE_INITIALIZED))
                .thenCompose(result -> setStateAfterList(protocolService.initialize(), State.PROTOCOL_SERVICE_INITIALIZED))
                .thenCompose(result -> CompletableFutureUtils.allOf(
                        walletService.tryAutoInitialization(),
                        userProfileService.initialize()
                                .thenCompose(res -> chatService.initialize()),
                        openOfferService.initialize(),
                        offerBookService.initialize(),
                        tradeIntentService.initialize()))
                // TODO Needs to increase if using embedded I2P router (i2p internal bootstrap timeouts after 5 mins)
                .orTimeout(5, TimeUnit.MINUTES)
                .thenApply(list -> list.stream().allMatch(e -> e))
                .whenComplete((list, throwable) -> {
                    if (throwable == null) {
                        log.info("All application services initialized");
                        setState(State.INIT_COMPLETE);
                    } else {
                        log.error("Error at initializing application services", throwable);
                        setState(State.INIT_FAILED);
                    }
                });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return runAsync(() -> openOfferService.shutdown()
                        .thenCompose(list -> {
                            marketPriceService.shutdown();
                            return networkService.shutdown()
                                    .whenComplete((__, throwable) -> {
                                        if (throwable != null) {
                                            log.error("Error at shutdown", throwable);
                                            System.exit(EXIT_FAILURE);
                                        } else {
                                            // In case the application is a JavaFXApplication give it chance to trigger the exit
                                            // via Platform.exit()
                                            runAsync(() -> System.exit(EXIT_SUCCESS));
                                        }
                                    });
                        })
                        .thenRun(walletService::shutdown)
                , ExecutorFactory.newSingleThreadExecutor("Shutdown"));
    }

    public KeyPairService getKeyPairService() {
        return securityService.getKeyPairService();
    }

    //todo move to wallet domain
    private boolean isRegtestRun() {
        return applicationConfig.isBitcoindRegtest() || applicationConfig.isElementsdRegtest();
    }

    private Optional<WalletConfig> createRegtestWalletConfig() {
        String walletsDataDir = applicationConfig.baseDir() + File.separator + "wallets";
        Path walletsDataDirPath = FileSystems.getDefault().getPath(walletsDataDir);

        WalletBackend walletBackend = applicationConfig.isBitcoindRegtest() ?
                WalletBackend.BITCOIND : WalletBackend.ELEMENTSD;

        var walletConfig = WalletConfig.builder()
                .walletBackend(walletBackend)
                .networkType(NetworkType.REGTEST)
                .hostname(Optional.empty())
                .port(Optional.empty())
                .user("bisq")
                .password("bisq")
                .walletsDataDirPath(walletsDataDirPath)
                .build();
        return Optional.of(walletConfig);
    }

    private CompletableFuture<Boolean> setState(State newState) {
        checkArgument(state.get().ordinal() < newState.ordinal(),
                "New state %s must have a higher ordinal as the current state %s", newState, state.get());
        state.set(newState);
        log.info("New state {}", newState);
        runAsync(() -> listeners.forEach(e -> e.onStateChanged(newState)), DISPATCHER);

        return CompletableFuture.completedFuture(true);
    }

    private CompletableFuture<Boolean> setStateAfter(CompletableFuture<Boolean> initTask, State stateAfter) {
        return initTask.thenCompose(result -> setState(stateAfter));
    }

    private CompletableFuture<Boolean> setStateAfterList(CompletableFuture<List<Boolean>> initTaskList, State stateAfter) {
        return initTaskList.thenCompose(result -> setState(stateAfter));
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
}
