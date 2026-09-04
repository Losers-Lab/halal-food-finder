"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Alert } from "@/components/auth/Alert";
import { Button } from "@/components/auth/Button";
import { Field } from "@/components/auth/Field";
import {
  createListingSchema,
  type CreateListingFormValues,
} from "@/lib/listings/schemas";

const EMPTY_VALUES: CreateListingFormValues = {
  name: "",
  address: "",
  lat: "",
  lng: "",
  cuisine: "",
  isHandCut: false,
  isDelivery: false,
};

type ListingFormProps = {
  /**
   * Called with the validated form values on submit. The consumer performs the
   * actual create/update API call and is responsible for setting `serverError`
   * on a typed failure; the component merely awaits it (driving isSubmitting)
   * and re-renders whatever `serverError` decodes to.
   */
  onSubmit: (values: CreateListingFormValues) => Promise<void>;
  /** Primary button label, e.g. "Add listing" or "Save changes". */
  submitLabel: string;
  /** Loading label while the submit is in flight, e.g. "Saving…". */
  submittingLabel: string;
  /** Top-of-card server/network error banner (sc-134 fail-fast detail). */
  serverError: string | null;
  /**
   * Prefill for editing an existing listing (sc-23). When omitted the form
   * starts empty (add mode). The caller renders this component only after the
   * listing has been loaded, so defaultValues are guaranteed at mount.
   */
  defaultValues?: CreateListingFormValues;
};

/**
 * Shared Add/Edit Restaurant Listing form (sc-138 add, sc-23 owner edit).
 *
 * The backend's update request (PATCH /v1/listings/{id}) reuses the add
 * request shape, so both screens render this same field set. The form handles
 * client-side validation (name/address/lat/lng/cuisine required, coordinate
 * bounds), the hand-cut (sc-42) and delivery (sc-184) tri-state checkboxes, and
 * the submit loading state; the consumer supplies the actual write via
 * `onSubmit` and surfaces typed server errors via `serverError`. The hand-cut
 * and delivery flags are round-tripped (checked → true, unchecked → omitted/
 * unknown) so an owner edit never silently clears a claimed flag.
 */
export function ListingForm({
  onSubmit,
  submitLabel,
  submittingLabel,
  serverError,
  defaultValues,
}: ListingFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<CreateListingFormValues>({
    resolver: zodResolver(createListingSchema),
    defaultValues: { ...EMPTY_VALUES, ...defaultValues },
  });

  return (
    <div className="rounded-lg border border-kraft-200 bg-ink-0 p-6 shadow-card sm:p-8">
      {serverError ? (
        <div className="mb-4">
          <Alert>{serverError}</Alert>
        </div>
      ) : null}

      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        <Field
          label="Restaurant name"
          error={errors.name?.message}
          inputProps={{
            ...register("name"),
            type: "text",
            autoComplete: "organization",
            placeholder: "e.g. Al-Amir Grill",
            disabled: isSubmitting,
          }}
        />

        <Field
          label="Address"
          error={errors.address?.message}
          inputProps={{
            ...register("address"),
            type: "text",
            autoComplete: "street-address",
            placeholder: "Full street address",
            disabled: isSubmitting,
          }}
        />

        <div className="grid grid-cols-2 gap-4">
          <Field
            label="Latitude"
            helper="e.g. 40.7128"
            error={errors.lat?.message}
            inputProps={{
              ...register("lat"),
              type: "text",
              inputMode: "decimal",
              autoComplete: "off",
              disabled: isSubmitting,
            }}
          />
          <Field
            label="Longitude"
            helper="e.g. -74.0060"
            error={errors.lng?.message}
            inputProps={{
              ...register("lng"),
              type: "text",
              inputMode: "decimal",
              autoComplete: "off",
              disabled: isSubmitting,
            }}
          />
        </div>
        <p className="text-small text-ink-500">
          Coordinates place this listing on the map. Address-to-coordinate
          lookup is coming in a later release.
        </p>

        <Field
          label="Cuisine"
          error={errors.cuisine?.message}
          inputProps={{
            ...register("cuisine"),
            type: "text",
            placeholder: "e.g. Middle Eastern",
            disabled: isSubmitting,
          }}
        />

        <label className="flex items-start gap-3">
          <input
            type="checkbox"
            disabled={isSubmitting}
            {...register("isHandCut")}
            className="mt-0.5 h-5 w-5 rounded border-kraft-300 text-brand-500 focus:outline-2 focus:outline-offset-2 focus:outline-brand-500"
          />
          <span>
            <span className="font-medium text-ink-900">Hand-cut (Zabiha)</span>
            <span className="block text-small text-ink-500">
              Check if the meat is slaughtered by hand. Leave unchecked if
              unknown.
            </span>
          </span>
        </label>

        <label className="flex items-start gap-3">
          <input
            type="checkbox"
            disabled={isSubmitting}
            {...register("isDelivery")}
            className="mt-0.5 h-5 w-5 rounded border-kraft-300 text-brand-500 focus:outline-2 focus:outline-offset-2 focus:outline-brand-500"
          />
          <span>
            <span className="font-medium text-ink-900">Offers delivery</span>
            <span className="block text-small text-ink-500">
              Check if the restaurant offers delivery. Leave unchecked if
              unknown or pickup-only.
            </span>
          </span>
        </label>

        <div className="pt-2">
          <Button
            type="submit"
            loading={isSubmitting}
            loadingLabel={submittingLabel}
            className="w-full"
          >
            {submitLabel}
          </Button>
        </div>
      </form>
    </div>
  );
}