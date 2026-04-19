# Git Workflow & Conventions Guide

**Version:** 1.0
**Last Updated:** 2024-02-15
**Status:** Mandatory for all team members

---

## Table of Contents

1. [Branch Naming Conventions](#branch-naming-conventions)
2. [Commit Message Standards](#commit-message-standards)
3. [Pull Request Guidelines](#pull-request-guidelines)
4. [Code Review Process](#code-review-process)
5. [Merge Strategy](#merge-strategy)
6. [Git Workflow](#git-workflow)
7. [Best Practices](#best-practices)
8. [Common Commands Reference](#common-commands-reference)

---

## Branch Naming Conventions

### Branch Types & Format

All branch names must follow this pattern:

```
<type>/<issue-number>-<short-description>
```

### Branch Types

| Type        | Purpose                                  | Example                             |
| ----------- | ---------------------------------------- | ----------------------------------- |
| `feature/`  | New features or enhancements             | `feature/123-user-authentication`   |
| `bugfix/`   | Bug fixes                                | `bugfix/456-login-error`            |
| `hotfix/`   | Critical production fixes                | `hotfix/789-security-patch`         |
| `refactor/` | Code refactoring without feature changes | `refactor/234-cleanup-auth-service` |
| `docs/`     | Documentation only changes               | `docs/567-api-documentation`        |
| `test/`     | Adding or updating tests                 | `test/890-integration-tests`        |
| `chore/`    | Build, dependencies, config changes      | `chore/345-update-dependencies`     |
| `spike/`    | Research or proof of concept             | `spike/678-websocket-research`      |

### Rules

✅ **DO:**

- Always include the GitHub issue number
- Use lowercase letters
- Use hyphens (-) to separate words
- Keep descriptions short but meaningful (2-4 words)
- Delete branch after merge

❌ **DON'T:**

- Use underscores or camelCase
- Create branches without issue numbers
- Use generic names like `fix`, `update`, `test`
- Keep stale branches after merge

### Examples

```bash
# Good ✅
feature/123-dark-mode-toggle
bugfix/456-password-validation-error
hotfix/789-critical-security-patch
refactor/234-extract-auth-logic
docs/567-setup-instructions

# Bad ❌
feature/dark-mode          # Missing issue number
Feature/123-DarkMode       # Wrong case
fix_login                  # Underscore, no issue number
test                       # Too generic
```

### Protected Branches

These branches are protected and require pull requests:

- `main` - Production-ready code
- `develop` - Integration branch for features (if using Git Flow)
- `staging` - Pre-production testing (optional)

**Rules for protected branches:**

- No direct commits allowed
- Require at least 1 approval before merge
- Must pass all CI/CD checks
- Must be up-to-date with base branch

---

## Commit Message Standards

### Format

We follow the **Conventional Commits** specification:

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Structure

```
type(scope): Brief description (max 72 characters)

Detailed explanation of what changed and why (optional).
Wrap at 72 characters per line.

Fixes #123
Refs #456
```

### Commit Types

| Type       | Description                             | Example                                          |
| ---------- | --------------------------------------- | ------------------------------------------------ |
| `feat`     | New feature                             | `feat(auth): add OAuth login support`            |
| `fix`      | Bug fix                                 | `fix(api): resolve null pointer in user service` |
| `docs`     | Documentation changes                   | `docs(readme): update installation steps`        |
| `style`    | Code style/formatting (no logic change) | `style(components): format with prettier`        |
| `refactor` | Code refactoring                        | `refactor(auth): simplify token validation`      |
| `test`     | Adding or updating tests                | `test(auth): add unit tests for login`           |
| `chore`    | Build, dependencies, tooling            | `chore(deps): upgrade react to v18.2`            |
| `perf`     | Performance improvements                | `perf(api): optimize database queries`           |
| `ci`       | CI/CD changes                           | `ci(github): add automated testing workflow`     |
| `revert`   | Revert previous commit                  | `revert: revert "feat(auth): add OAuth"`         |

### Scope (Optional but Recommended)

The scope specifies what part of the codebase is affected:

- `auth` - Authentication/authorization
- `api` - API/backend services
- `ui` - User interface components
- `db` - Database related
- `config` - Configuration files
- `deps` - Dependencies

### Subject Line Rules

✅ **DO:**

- Use imperative mood ("add" not "added" or "adds")
- Don't capitalize first letter
- No period at the end
- Keep under 72 characters
- Be specific and concise

❌ **DON'T:**

- Write vague messages like "fix bug" or "update code"
- Use past tense
- Add unnecessary punctuation

### Body (Optional)

Use the body to explain:

- **What** changed and **why** (not how - code shows that)
- Motivation for the change
- Contrast with previous behavior

Wrap text at 72 characters.

### Footer (Required for Issues)

Link to related issues:

```
Fixes #123          # Closes the issue when merged
Closes #456         # Alternative to Fixes
Refs #789           # References but doesn't close
Related to #234     # Related issue
```

For breaking changes:

```
BREAKING CHANGE: API endpoints now require authentication token
```

### Examples

#### Simple Commit

```bash
git commit -m "feat(auth): add password reset functionality"
```

#### Detailed Commit

```bash
git commit -m "fix(api): resolve race condition in user registration

The registration endpoint was experiencing intermittent failures due to
concurrent database writes. Added transaction locking to ensure atomic
operations during user creation and profile setup.

- Added database transaction wrapper
- Implemented retry logic for deadlock scenarios
- Updated error handling to provide specific feedback

Fixes #456
Refs #789"
```

#### More Examples

```bash
# Feature
feat(dashboard): add real-time notification widget

# Bug fix
fix(auth): prevent duplicate email registration

# Documentation
docs(api): document authentication endpoints

# Refactoring
refactor(components): extract reusable button component

# Performance
perf(queries): add database indexes for user lookups

# Testing
test(integration): add API endpoint tests

# Dependency update
chore(deps): update axios to v1.6.0

# Breaking change
feat(api): migrate to v2 authentication flow

BREAKING CHANGE: Authentication now requires OAuth tokens instead of
API keys. Update client applications to use the new flow.

Refs #234
```

### Commit Frequency

- Commit **early and often** with logical chunks
- One commit = one logical change
- Each commit should be buildable and testable
- Avoid "work in progress" commits on shared branches

---

## Pull Request Guidelines

### PR Title Format

Follow the same convention as commit messages:

```
<type>(scope): Brief description

Example:
feat(auth): add two-factor authentication
```

### PR Description Template

```markdown
## Description

Brief summary of what this PR does and why.

## Type of Change

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update
- [ ] Refactoring (no functional changes)

## Related Issues

Closes #123
Refs #456

## Changes Made

- Change 1
- Change 2
- Change 3

## Testing

- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing completed
- [ ] All tests passing locally

## Screenshots (if applicable)

[Add screenshots for UI changes]

## Checklist

- [ ] Code follows project style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex logic
- [ ] Documentation updated (README, API docs, etc.)
- [ ] No new warnings or errors
- [ ] Branch is up-to-date with base branch
- [ ] Commit messages follow conventions

## Reviewer Notes

Any specific areas to focus on during review?

## Deployment Notes

Any special considerations for deployment?
```

### PR Rules

✅ **Required Before Creating PR:**

- Branch is up-to-date with base branch
- All tests pass locally
- Code has been self-reviewed
- No commented-out code or debug logs
- Linting passes with no errors

✅ **Required Before Merging:**

- At least 1 approval from team member
- All CI/CD checks passing
- No unresolved conversations
- Conflicts resolved
- Documentation updated

### PR Size Guidelines

Keep PRs manageable:

- **Ideal:** < 400 lines of code changes
- **Maximum:** < 800 lines (if larger, consider splitting)
- **Rationale:** Easier to review, faster to merge, less risky

If PR is too large:

```markdown
## Part 1: Database Schema Changes

PR #123 (merged)

## Part 2: API Implementation

PR #124 (current)

## Part 3: Frontend Integration

PR #125 (planned)
```

---

## Code Review Process

### Reviewer Responsibilities

1. **Review within 24 hours** (or notify if unavailable)
2. **Check for:**
   - Code quality and readability
   - Logic errors and edge cases
   - Test coverage
   - Performance implications
   - Security vulnerabilities
   - Documentation completeness

3. **Provide constructive feedback:**
   - Be specific about what to change
   - Explain the "why" behind suggestions
   - Distinguish between blocking issues and suggestions

### Review Labels

Use these prefixes in review comments:

- `[BLOCKING]` - Must be fixed before merge
- `[SUGGESTION]` - Nice to have, not required
- `[QUESTION]` - Need clarification
- `[NITPICK]` - Minor style/preference issue

**Example:**

```
[BLOCKING] This function doesn't handle null values. Add validation before processing.

[SUGGESTION] Consider extracting this logic into a separate utility function for reusability.

[QUESTION] Why did we choose this approach over using the existing service?

[NITPICK] Variable name could be more descriptive: `userData` instead of `data`
```

### Review Response

As the PR author:

- Respond to all comments
- Mark conversations as resolved when addressed
- Don't take feedback personally
- Ask for clarification if needed
- Thank reviewers for their time

---

## Merge Strategy

### Merge Methods

We use **Squash and Merge** as the default strategy:

```
✅ Squash and Merge (Default)
- All commits squashed into one
- Clean linear history
- PR title becomes commit message
- Use for: most feature branches

⚠️ Merge Commit (Special Cases Only)
- Preserves all commits
- Creates merge commit
- Use for: long-running features, releases

❌ Rebase and Merge (Not Used)
- Rewrites history
- Can cause issues with shared branches
```

### After Merge

```bash
# Delete the merged branch (automatic via GitHub settings)
# Update your local repository
git checkout main
git pull origin main

# Delete local branch
git branch -d feature/123-branch-name

# Delete remote branch (if not auto-deleted)
git push origin --delete feature/123-branch-name
```

---

## Git Workflow

### Standard Workflow (Feature Branch)

```bash
# 1. Start from main branch
git checkout main
git pull origin main

# 2. Create feature branch from issue
git checkout -b feature/123-add-user-profile

# 3. Make changes and commit frequently
git add .
git commit -m "feat(profile): add user avatar upload"

# 4. Push to remote
git push origin feature/123-add-user-profile

# 5. Keep branch updated with main (if working for multiple days)
git checkout main
git pull origin main
git checkout feature/123-add-user-profile
git merge main
# Or use rebase if you prefer:
# git rebase main

# 6. Create Pull Request on GitHub

# 7. Address review feedback
git add .
git commit -m "fix(profile): handle missing avatar gracefully"
git push origin feature/123-add-user-profile

# 8. After merge, delete branch and update local
git checkout main
git pull origin main
git branch -d feature/123-add-user-profile
```

### Hotfix Workflow (Critical Production Issue)

```bash
# 1. Create hotfix branch from main
git checkout main
git pull origin main
git checkout -b hotfix/789-security-vulnerability

# 2. Fix the issue
git add .
git commit -m "fix(auth): patch SQL injection vulnerability"

# 3. Push and create PR immediately
git push origin hotfix/789-security-vulnerability

# 4. Fast-track review and merge
# 5. Deploy to production immediately
# 6. Merge hotfix into develop (if using Git Flow)
```

### Working with Long-Running Branches

```bash
# Keep feature branch updated with main regularly
git checkout feature/123-large-feature
git fetch origin
git merge origin/main

# Resolve conflicts if any
git add .
git commit -m "chore: merge main into feature branch"
git push origin feature/123-large-feature
```

---

## Best Practices

### Daily Workflow

```bash
# Start of day - update main
git checkout main
git pull origin main

# Check what you were working on
git branch --list

# Resume work on feature
git checkout feature/123-your-feature

# Commit at logical checkpoints (not end of day)
# Push at least once per day
git push origin feature/123-your-feature
```

### Commit Hygiene

✅ **DO:**

- Write meaningful commit messages
- Commit logical units of work
- Test before committing
- Review your own diff before committing

❌ **DON'T:**

- Commit commented-out code
- Commit console.logs or debug statements
- Commit sensitive data (API keys, passwords)
- Commit node_modules or build artifacts
- Create "WIP" or "temp" commits on shared branches

### Handling Mistakes

**Undo last commit (not pushed):**

```bash
git reset --soft HEAD~1    # Keep changes staged
git reset HEAD~1           # Keep changes unstaged
git reset --hard HEAD~1    # Discard changes
```

**Amend last commit message:**

```bash
git commit --amend -m "fix(auth): correct typo in commit message"
```

**Revert a pushed commit:**

```bash
git revert <commit-hash>
git push origin branch-name
```

**Undo changes in file:**

```bash
git checkout -- filename.js    # Before staging
git reset HEAD filename.js     # After staging
```

---

## Common Commands Reference

### Setup

```bash
# Configure user (first time)
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"

# Clone repository
git clone https://github.com/your-org/repo-name.git
cd repo-name
```

### Daily Commands

```bash
# Check status
git status

# View changes
git diff                    # Unstaged changes
git diff --staged          # Staged changes
git diff main              # Compare with main

# Stage changes
git add filename.js        # Specific file
git add .                  # All changes
git add -p                 # Interactive staging

# Commit
git commit -m "type(scope): message"
git commit --amend         # Modify last commit

# Push/Pull
git push origin branch-name
git pull origin main
git fetch origin           # Download without merging
```

### Branch Management

```bash
# Create and switch
git checkout -b feature/123-new-feature

# Switch branches
git checkout main
git switch feature/123-new-feature  # Modern alternative

# List branches
git branch                 # Local branches
git branch -r              # Remote branches
git branch -a              # All branches

# Delete branches
git branch -d branch-name          # Safe delete (merged only)
git branch -D branch-name          # Force delete
git push origin --delete branch-name  # Delete remote

# Rename branch
git branch -m old-name new-name
```

### Stashing (Save Work Temporarily)

```bash
# Stash changes
git stash                  # Save and clean working directory
git stash save "WIP: user profile work"

# List stashes
git stash list

# Apply stash
git stash pop              # Apply and remove stash
git stash apply            # Apply but keep stash
git stash apply stash@{2}  # Apply specific stash

# Delete stash
git stash drop stash@{0}
git stash clear            # Delete all stashes
```

### History

```bash
# View log
git log                              # Full log
git log --oneline                    # Compact
git log --graph --oneline --all     # Visual graph
git log -p filename.js              # Changes to specific file

# Search commits
git log --grep="auth"               # Search commit messages
git log --author="John"             # By author
git log --since="2 weeks ago"       # Time range
```

### Merge & Rebase

```bash
# Merge main into feature
git checkout feature/123-branch
git merge main

# Rebase (rewrite history - use carefully)
git rebase main

# Abort merge/rebase
git merge --abort
git rebase --abort

# Continue after resolving conflicts
git rebase --continue
```

---

## Git Ignore Patterns

Ensure `.gitignore` includes:

```gitignore
# Dependencies
node_modules/
vendor/

# Environment variables
.env
.env.local
.env.*.local

# Build outputs
dist/
build/
*.log

# IDE files
.vscode/
.idea/
*.swp
*.swo
*~

# OS files
.DS_Store
Thumbs.db

# Test coverage
coverage/
.nyc_output/

# Temporary files
tmp/
temp/
*.tmp
```

---

## Troubleshooting

### Common Issues

**"Your branch is behind 'origin/main'"**

```bash
git pull origin main
```

**"Merge conflict in file.js"**

```bash
# 1. Open file and resolve conflicts manually
# 2. Remove conflict markers (<<<<, ====, >>>>)
# 3. Stage resolved file
git add file.js
# 4. Complete merge
git commit -m "chore: resolve merge conflicts"
```

**"I committed to main by mistake"**

```bash
# If not pushed yet
git reset --soft HEAD~1
git stash
git checkout -b feature/123-correct-branch
git stash pop
git add .
git commit -m "feat(scope): correct commit"
```

**"I need to undo a pushed commit"**

```bash
# Use revert (creates new commit that undoes changes)
git revert <commit-hash>
git push origin branch-name
```

---

## Enforcement

### Automated Checks

We use the following tools to enforce standards:

1. **Commitlint** - Validates commit messages
2. **Husky** - Git hooks for pre-commit/pre-push checks
3. **ESLint/Prettier** - Code style enforcement
4. **GitHub Actions** - CI/CD pipeline validation

### Pre-commit Hooks

```bash
# Install Husky (one-time setup)
npm install --save-dev husky @commitlint/cli @commitlint/config-conventional

# Enable hooks
npx husky install

# Add commit-msg hook
npx husky add .husky/commit-msg 'npx --no -- commitlint --edit "$1"'

# Add pre-commit hook
npx husky add .husky/pre-commit 'npm run lint && npm test'
```

This will:

- Reject commits with invalid messages
- Run linting before commit
- Run tests before push

---

## Team Agreement

By contributing to this project, all team members agree to:

✅ Follow these Git conventions consistently
✅ Review and approve PRs within 24 hours
✅ Write meaningful commit messages
✅ Keep branches up-to-date
✅ Delete branches after merge
✅ Ask questions if unsure
✅ Suggest improvements to this guide

---

## Resources

- [Conventional Commits](https://www.conventionalcommits.org/)
- [Git Best Practices](https://git-scm.com/book/en/v2)
- [How to Write a Git Commit Message](https://chris.beams.io/posts/git-commit/)
- [GitHub Flow](https://guides.github.com/introduction/flow/)

---

## Changelog

| Version | Date       | Changes         |
| ------- | ---------- | --------------- |
| 1.0     | 2024-02-15 | Initial version |

---

**Questions or suggestions?** Open an issue or discuss in team meetings.

**Remember:** These conventions exist to make our collaboration smoother and our codebase more maintainable. When in doubt, ask the team! 🚀
