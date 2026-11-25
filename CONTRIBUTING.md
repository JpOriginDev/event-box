# Contributing to Event Box

Thanks for your interest in improving Event Box! Bug reports, ideas and pull requests are welcome.

## Reporting bugs and requesting features

Open an [issue](https://github.com/JpOriginDev/event-box/issues/new/choose) and pick the matching
template. Please include the Event Box version, Android version and device, and steps to
reproduce.

## Development setup

1. Fork and clone the repository.
2. Open it in Android Studio (or build from the command line).
3. Build and run the sample app:

   ```
   ./gradlew :app:assembleDebug
   ```

The library lives in the `eventBox` module and the sample app in `app`.

## Pull requests

1. Create a branch from `main`.
2. Keep the change focused: one fix or feature per pull request.
3. Follow the existing Kotlin style and keep the library free of extra permissions and heavy
   dependencies (`minSdk 21`).
4. Build the project and try your change on a device or emulator.
5. Add a line to [CHANGELOG.md](CHANGELOG.md) for user-visible changes.
6. Open the pull request and fill in the template.

## License

By contributing, you agree that your contributions are licensed under the project's
[MIT License](LICENSE).

Please also follow our [Code of Conduct](CODE_OF_CONDUCT.md).
