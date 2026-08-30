"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense } from "react";
import Link from "next/link";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { AuthCard } from "@/components/auth/AuthCard";
import { Alert } from "@/components/auth/Alert";
import { Button } from "@/components/auth/Button";
import { Field } from "@/components/auth/Field";
import { PasswordField } from "@/components/auth/PasswordField";
import { api, ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { loginSchema, type LoginFormValues } from "@/lib/auth/schemas";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { signIn } = useAuth();
  const [serverError, setServerError] = useState<string | null>(null);
  const [credentialError, setCredentialError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  async function onSubmit(values: LoginFormValues) {
    setServerError(null);
    setCredentialError(null);
    try {
      const auth = await api.login(values);
      signIn(auth, values.email);
      router.push("/");
    } catch (err) {
      if (err instanceof ApiError) {
        // Single combined message — never reveal which field failed (anti-enumeration).
        if (err.code === "invalid_credentials") {
          setCredentialError("Incorrect email or password.");
          // DO NOT clear the email; DO clear the password and refocus it.
          setValue("password", "");
          setFocus("password");
          return;
        }
      }
      setServerError("Something went wrong. Please try again.");
    }
  }

  const created = searchParams.get("created") === "1";

  return (
    <AuthCard
      heading="Welcome back"
      subcopy="Log in to continue finding verified halal food."
      footer={
        <>
          New here?{" "}
          <Link
            href="/signup"
            className="font-medium text-brand-500 hover:text-brand-600 hover:underline"
          >
            Sign up
          </Link>
        </>
      }
    >
      {created ? (
        <div
          role="status"
          className="mb-4 rounded-md border border-ink-300 bg-ink-100 px-3 py-2.5 text-small text-ink-700"
        >
          Account created. Log in to continue.
        </div>
      ) : null}

      {serverError ? <Alert variant="banner">{serverError}</Alert> : null}

      {credentialError ? (
        <div className="mb-4">
          <Alert variant="inline">{credentialError}</Alert>
        </div>
      ) : null}

      <form
        onSubmit={handleSubmit(onSubmit)}
        noValidate
        className="space-y-4"
      >
        <Field
          label="Email"
          error={errors.email?.message}
          inputProps={{
            ...register("email"),
            type: "email",
            autoComplete: "email",
            placeholder: "you@example.com",
            disabled: isSubmitting,
          }}
        />

        <PasswordField
          label="Password"
          error={errors.password?.message}
          labelEnd={
            <Link
              href="#"
              className="text-small text-brand-500 hover:text-brand-600 hover:underline"
              onClick={(e) => e.preventDefault()}
            >
              Forgot password?
            </Link>
          }
          inputProps={{
            ...register("password"),
            autoComplete: "current-password",
            disabled: isSubmitting,
          }}
        />

        <div className="pt-2">
          <Button
            type="submit"
            loading={isSubmitting}
            loadingLabel="Logging in…"
            className="w-full"
          >
            Log in
          </Button>
        </div>
      </form>
    </AuthCard>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}