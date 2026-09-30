package dev.ayman.seed.wizard;

/** Signals that the user ended terminal input before completing the wizard. */
public class WizardCancelledException extends RuntimeException
{
    public WizardCancelledException()
    {
        super("Wizard cancelled.");
    }
}
