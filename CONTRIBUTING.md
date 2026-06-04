# Contributing to Koya Launcher

We welcome contributions from the community! To contribute, please follow these guidelines:

## Code of Conduct
By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

## Getting Started
1. **Fork** the repository on GitHub.
2. **Clone** your fork locally.
3. Create a new branch for your changes:
   - For features: `feature/your-feature-name`
   - For bug fixes: `bugfix/your-bugfix-name`

## Coding Standards
- Write clean, readable Kotlin code.
- Ensure all resources and user-facing text strings are localized in `strings.xml`.
- Do not commit any secret variables, credentials, or keystores.
- Verify your changes compile without warning flags:
  ```bash
  ./gradlew compileDebugKotlin
  ```

## Submitting a Pull Request
1. Commit your changes with clear, descriptive commit messages.
2. Push your branch to your fork on GitHub.
3. Open a Pull Request against our `main` branch.
4. Ensure your PR description lists the problems solved and changes introduced. Refer to the checklist in our [Pull Request Template](.github/PULL_REQUEST_TEMPLATE.md).
