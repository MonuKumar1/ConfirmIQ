package com.meridian.confirmiq.confirmation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ActionRequest(@NotNull ConfirmationAction action,
                            @Size(max = 1000) String comment,
                            @Valid AmendRequest amendment) {}
