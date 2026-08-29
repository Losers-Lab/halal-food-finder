"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { AuthCard } from "@/components/auth/AuthCard";
import { Alert } from "@/components/auth/Alert";
import { Button } from "@/components/auth/Button";
import { Field } from "@/components/auth/Field";
import { PasswordField } from "@/components/auth/PasswordField";
import { api, ApiError } from "@/lib/api/client";
import { signupSchema, type SignupFormValues } from "@/lib/auth/schemas";

export default function SignUpPage() {
  const router = useRouter();
  const [serverError, setServerError] = useState<string | null>(null);
  // Server-reported duplicate email. Tracked separately so "logging in" can be
  // rendered as an inline <Link> inside the field error (spec: auth-screens.md
  // §1 error treatment #1 — always offer the next step). Cleared on edit.
  const [emailConflict, setEmailConflict] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SignupFormValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: { email: "", password: "" },
  });

  async function onSubmit(values: SignupFormValues) {
    setServerError(null);
    setEmailConflict(false);
    try {
      await api.signup(values);
      // Post-signup destination: backend returns the account (no session) —
      // so we can't auto-login. Surface a success notice and route to Log In.
      router.push("/login?created=1");
    } catch (err) {
      if (err instanceof ApiError) {
        switch (err.code) {
          case "email_already_exists":
            setEmailConflict(true);
            return;
          case "weak_password": {
            // Backend rule message (e.g. "Password must be at least 8 characters.").
            const detail = err.detail?.trim();
            setError("password", {
              type: "server",
              message:
                detail && /^\s*$/i.test(detail) === false
                  ? detail
                  : "Password is too weak. Use at least 8 characters.",
            });
            return;
          }
          case "invalid_input":
            // Fall through to a generic banner; the specific field error is
            // usually caught client-side first.
            break;
          default:
            break;
        }
      }
      setServerError("Something went wrong. Please try again.");
    }
  }

  return (
    <AuthCard
      heading="Create your account"
      subcopy="Find verified halal food near you."
      trustLine
      footer={
        <>
          Already have an account?{" "}
          <Link
            href="/login"
            className="font-medium text-brand-500 hover:text-brand-600 hover:underline"
          >
            Log in
          </Link>
        </>
      }
    >
      {serverError ? <Alert variant="banner">{serverError}</Alert> : null}

      <form
        onSubmit={handleSubmit(onSubmit)}
        noValidate
        className="space-y-4"
      >
        <Field
          label="Email"
          error={
            emailConflict ? (
              <>
                An account with this email already exists. Try{" "}
                <Link
                  href="/login"
                  className="font-medium text-brand-600 underline underline-offset-2 hover:text-brand-700"
                >
                  logging in
                </Link>{" "}
                instead.
              </>
            ) : (
              errors.email?.message
            )
          }
          inputProps={{
            ...register("email"),
            onChange: (e) => {
              if (emailConflict) setEmailConflict(false);
              void register("email").onChange(e);
            },
            type: "email",
            autoComplete: "email",
            placeholder: "you@example.com",
            disabled: isSubmitting,
          }}
        />

        <PasswordField
          label="Password"
          error={errors.password?.message}
          helper="At least 8 characters."
          inputProps={{
            ...register("password"),
            autoComplete: "new-password",
            disabled: isSubmitting,
          }}
        />

        <div className="pt-2">
          <Button
            type="submit"
            loading={isSubmitting}
            loadingLabel="Creating account…"
            className="w-full"
          >
            Create account
          </Button>
        </div>
      </form>
    </AuthCard>
  );
}