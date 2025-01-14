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

package bisq.desktop.primary.main.content.social.onboarding.onboardNewbie;

import bisq.application.DefaultApplicationService;
import bisq.common.observable.Pin;
import bisq.desktop.Navigation;
import bisq.desktop.NavigationTarget;
import bisq.desktop.common.observable.FxBindings;
import bisq.desktop.common.threading.UIThread;
import bisq.desktop.common.utils.KeyWordDetection;
import bisq.desktop.common.view.Controller;
import bisq.desktop.overlay.Popup;
import bisq.desktop.primary.main.content.trade.components.MarketSelection;
import bisq.i18n.Res;
import bisq.offer.spec.Direction;
import bisq.presentation.formatters.AmountFormatter;
import bisq.social.chat.ChatService;
import bisq.social.intent.TradeIntentService;
import com.google.common.base.Joiner;
import javafx.beans.InvalidationListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.HashSet;

import static bisq.desktop.primary.main.content.social.onboarding.onboardNewbie.OnboardNewbieModel.MAX_INPUT_TERMS;

@Slf4j
public class OnboardNewbieController implements Controller {
    private final OnboardNewbieModel model;
    @Getter
    private final OnboardNewbieView view;
    private final MarketSelection marketSelection;
    private final BtcFiatAmountGroup btcFiatAmountGroup;
    private final PaymentMethodsSelection paymentMethodsSelection;
    private final ChatService chatService;
    private final TradeIntentService tradeIntentService;
    private Pin tradeTagsPin, currencyTagsPin, paymentMethodTagsPin, customTagsPin;

    private Subscription selectedMarketSubscription, baseSideAmountSubscription;
    private final InvalidationListener paymentMethodsSelectionListener;
    private Subscription termsDisabledSubscription;

    public OnboardNewbieController(DefaultApplicationService applicationService) {
        tradeIntentService = applicationService.getTradeIntentService();
        chatService = applicationService.getChatService();
        model = new OnboardNewbieModel(applicationService.getUserProfileService().getPersistableStore().getSelectedUserProfile().get().userName());

        marketSelection = new MarketSelection(applicationService.getSettingsService());
        btcFiatAmountGroup = new BtcFiatAmountGroup(applicationService.getMarketPriceService());

        paymentMethodsSelection = new PaymentMethodsSelection(chatService);

        view = new OnboardNewbieView(model, this,
                marketSelection.getRoot(),
                btcFiatAmountGroup.getRoot(),
                paymentMethodsSelection);

        Direction direction = Direction.BUY;
        btcFiatAmountGroup.setDirection(direction);
        paymentMethodsSelection.setDirection(direction);

        paymentMethodsSelectionListener = o -> {
            model.getSelectedPaymentMethods().setAll(paymentMethodsSelection.getSelectedPaymentMethods());
            updateOfferPreview();
        };
    }

    @Override
    public void onActivate() {
        selectedMarketSubscription = EasyBind.subscribe(marketSelection.selectedMarketProperty(),
                selectedMarket -> {
                    model.setSelectedMarket(selectedMarket);
                    btcFiatAmountGroup.setSelectedMarket(selectedMarket);
                    paymentMethodsSelection.setSelectedMarket(selectedMarket);
                    chatService.findPublicChannelForMarket(selectedMarket).ifPresent(publicChannel -> {
                        tradeTagsPin = FxBindings.<String, String>bind(model.getTradeTags()).map(String::toUpperCase).to(publicChannel.getTradeTags());
                        currencyTagsPin = FxBindings.<String, String>bind(model.getCurrencyTags()).map(String::toUpperCase).to(publicChannel.getCurrencyTags());
                        paymentMethodTagsPin = FxBindings.<String, String>bind(model.getPaymentMethodsTags()).map(String::toUpperCase).to(publicChannel.getPaymentMethodTags());
                        customTagsPin = FxBindings.<String, String>bind(model.getCustomTags()).map(String::toUpperCase).to(publicChannel.getCustomTags());
                    });

                    updateOfferPreview();
                });
        baseSideAmountSubscription = EasyBind.subscribe(btcFiatAmountGroup.baseSideAmountProperty(),
                e -> {
                    model.setBaseSideAmount(e);
                    updateOfferPreview();
                });

        termsDisabledSubscription = EasyBind.subscribe(model.getTerms(),
                text -> {
                    model.getTermsEditable().set(text == null || text.length() <= MAX_INPUT_TERMS);
                    if (text != null && text.length() > MAX_INPUT_TERMS) {
                        String truncated = model.getTerms().get().substring(0, MAX_INPUT_TERMS);
                        UIThread.runOnNextRenderFrame(() -> model.getTerms().set(truncated));
                    }
                });

        paymentMethodsSelection.getSelectedPaymentMethods().addListener(paymentMethodsSelectionListener);

        model.getSelectedPaymentMethods().setAll(paymentMethodsSelection.getSelectedPaymentMethods());
        model.getTerms().set(Res.get("satoshisquareapp.createOffer.defaultTerms"));

        updateOfferPreview();
    }

    @Override
    public void onDeactivate() {
        selectedMarketSubscription.unsubscribe();
        baseSideAmountSubscription.unsubscribe();
        termsDisabledSubscription.unsubscribe();
        paymentMethodsSelection.getSelectedPaymentMethods().removeListener(paymentMethodsSelectionListener);
        if (tradeTagsPin != null) {
            tradeTagsPin.unbind();
            currencyTagsPin.unbind();
            paymentMethodTagsPin.unbind();
            customTagsPin.unbind();
        }
    }

    void onCreateOffer() {
        tradeIntentService.publishTradeIntent(model.getSelectedMarket(),
                        model.getBaseSideAmount().getValue(),
                        new HashSet<>(model.getSelectedPaymentMethods()),
                        model.getTerms().get())
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        String channelName = chatService.findPublicChannelForMarket(model.getSelectedMarket()).orElseThrow().getChannelName();
                        new Popup().confirmation(Res.get("satoshisquareapp.createOffer.publish.success", channelName))
                                .actionButtonText(Res.get("satoshisquareapp.createOffer.publish.goToChat", channelName))
                                .onAction(() -> Navigation.navigateTo(NavigationTarget.CHAT))
                                .hideCloseButton()
                                .show();
                    } else {
                        //todo
                        new Popup().error(throwable.toString()).show();
                    }
                });
    }

    void onSkip() {
        Navigation.navigateTo(NavigationTarget.CHAT);
    }

    private void updateOfferPreview() {
        boolean isInvalidTradeIntent = isInvalidTradeIntent();
        model.getIsInvalidTradeIntent().set(isInvalidTradeIntent);
        if (isInvalidTradeIntent) {
            return;
        }

        String amount = AmountFormatter.formatAmountWithCode(model.getBaseSideAmount(), true);
        String quoteCurrency = model.getSelectedMarket().quoteCurrencyCode();
        String paymentMethods = Joiner.on(", ").join(model.getSelectedPaymentMethods());

        String previewText = Res.get("satoshisquareapp.createOffer.offerPreview", amount, quoteCurrency, paymentMethods);
        model.getStyleSpans().set(KeyWordDetection.getStyleSpans(previewText,
                model.getTradeTags(),
                model.getCurrencyTags(),
                model.getPaymentMethodsTags(),
                model.getCustomTags()));
        model.getOfferPreview().set(previewText);
    }

    private boolean isInvalidTradeIntent() {
        return model.getBaseSideAmount() == null ||
                model.getSelectedMarket() == null ||
                model.getSelectedPaymentMethods() == null ||
                model.getSelectedPaymentMethods().isEmpty();
    }
}
