package bisq.desktop.common.utils.validation;

import com.jfoenix.validation.base.ValidatorBase;

public class JFXInputValidator extends ValidatorBase {

    public JFXInputValidator() {
        super();
    }

    @Override
    protected void eval() {
        //Do nothing as validation is handled by current validation logic
    }

    public void resetValidation() {
        message.set(null);
        hasErrors.set(false);
    }

    public void applyErrorMessage(InputValidator.ValidationResult newValue) {
        applyErrorMessage(newValue.errorMessage);
    }

    public void applyErrorMessage(String errorMessage) {
        message.set(errorMessage);
        hasErrors.set(true);
    }
}
