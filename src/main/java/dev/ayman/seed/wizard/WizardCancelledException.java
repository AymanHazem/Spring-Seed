package dev.ayman.seed.wizard;
public class WizardCancelledException extends RuntimeException
{
    public WizardCancelledException()
    {
        super("Wizard cancelled.");
    }
}
