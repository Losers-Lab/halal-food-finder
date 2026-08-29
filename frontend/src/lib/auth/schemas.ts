import { z } from "zod";

/** Shared Zod schemas for auth forms. Copy matches the auth-screens.md error text. */

export const emailSchema = z
  .string()
  .trim()
  .min(1, "Enter a valid email address.")
  .email("Enter a valid email address.");

/** Login password — non-empty, client-side only (server does the real check). */
export const loginPasswordSchema = z
  .string()
  .min(1, "Enter your password.");

/** Sign-up password — mirrors backend PasswordPolicy.MIN_LENGTH = 8. */
export const signupPasswordSchema = z
  .string()
  .min(8, "Password is too weak. Use at least 8 characters.");

export const loginSchema = z.object({
  email: emailSchema,
  password: loginPasswordSchema,
});

export const signupSchema = z.object({
  email: emailSchema,
  password: signupPasswordSchema,
});

export type LoginFormValues = z.infer<typeof loginSchema>;
export type SignupFormValues = z.infer<typeof signupSchema>;