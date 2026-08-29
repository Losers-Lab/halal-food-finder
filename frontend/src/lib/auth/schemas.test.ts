import { describe, expect, it } from "vitest";
import { loginSchema, signupSchema } from "@/lib/auth/schemas";

describe("auth schemas", () => {
  describe("signup", () => {
    it("accepts a valid email and strong password", () => {
      const r = signupSchema.safeParse({ email: "a@b.co", password: "password1" });
      expect(r.success).toBe(true);
    });

    it("rejects a malformed email with the correct copy", () => {
      const r = signupSchema.safeParse({ email: "not-an-email", password: "password1" });
      expect(r.success).toBe(false);
      if (!r.success) {
        expect(r.error.issues[0]?.message).toBe("Enter a valid email address.");
      }
    });

    it("rejects a short password (backend MIN_LENGTH = 8)", () => {
      const r = signupSchema.safeParse({ email: "a@b.co", password: "short" });
      expect(r.success).toBe(false);
      if (!r.success) {
        expect(r.error.issues[0]?.message).toBe(
          "Password is too weak. Use at least 8 characters.",
        );
      }
    });
  });

  describe("login", () => {
    it("rejects an empty password with 'Enter your password.'", () => {
      const r = loginSchema.safeParse({ email: "a@b.co", password: "" });
      expect(r.success).toBe(false);
      if (!r.success) {
        expect(r.error.issues[0]?.message).toBe("Enter your password.");
      }
    });

    it("accepts valid credentials", () => {
      const r = loginSchema.safeParse({ email: "a@b.co", password: "anything" });
      expect(r.success).toBe(true);
    });
  });
});