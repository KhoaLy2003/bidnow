# 🤝 Contributing Guide

Thank you for contributing to this project!
This document defines the workflow and rules to ensure consistency and code quality across the team.

---

# 📂 Project Structure

```
/backend
/frontend
/docs
```

---

# 🌿 Branching Strategy

We follow a simple Git flow:

- `main` → stable, production-ready code
- `develop` (optional) → integration branch (if needed)
- Feature branches:
  - `feature/<feature-name>`
  - `bugfix/<bug-name>`
  - `hotfix/<hotfix-name>`

### Examples

```
feature/login-api
bugfix/payment-calculation
hotfix/null-pointer-auth
```

---

# 📝 Commit Convention

We follow **Conventional Commits** :

```
<type>(optional-scope): <short description>
```

### Types

- `feat` → new feature
- `fix` → bug fix
- `refactor` → code improvement (no behavior change)
- `docs` → documentation
- `style` → formatting
- `test` → testing
- `chore` → config, setup

### Examples

```
feat(auth): add login API
fix(payment): incorrect total calculation
docs: add contributing guide
```

---

# 📌 Issue Workflow

All work must be tracked via Issues.

### Steps:

1. Create or pick an Issue
2. Move it to **In Progress**
3. Assign yourself
4. Link PR to the Issue

---

# 🚀 Development Workflow

### 1. Create branch

```
git checkout -b feature/<name>
```

### 2. Develop & commit

- Follow commit convention
- Keep commits small and meaningful

### 3. Push branch

```
git push origin <branch-name>
```

### 4. Create Pull Request

---

# 🔍 Pull Request Rules

Each PR must:

- Have a clear title
- Link related Issue (e.g., `closes #12`)
- Describe what was done
- Be reviewed before merging

---

# ✅ Definition of Done

A task is considered **Done** when:

- Code is completed
- Meets acceptance criteria
- Reviewed and approved
- Merged into `main`
- No critical bugs remain

---

# 🔄 Code Review Guidelines

- Keep PR small and focused
- Review logic, not just syntax
- Suggest improvements clearly
- Avoid blocking without reason

---

# ⏱ Work In Progress (WIP)

- Limit to **1–2 tasks per person**
- Avoid working on too many tasks at once

---

# 🧪 Testing

- Test your code before creating PR
- Ensure no obvious bugs
- Add tests if applicable

---

# 📚 Documentation

- Update docs if behavior changes
- Keep README and `/docs` in sync

---

# 💡 General Principles

- Keep things simple
- Communicate clearly
- Write clean, readable code
- Follow the agreed workflow

---

# ❗ Notes

This is a lightweight process designed for a small team.
We prioritize **clarity, consistency, and efficiency** over complexity.

---

Happy coding! 🚀
