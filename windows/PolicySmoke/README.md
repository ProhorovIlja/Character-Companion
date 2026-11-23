# Manual Windows URL-handoff smoke test

`dotnet run --project windows/PolicySmoke/CharacterCompanion.PolicySmoke.csproj`

- Uses the **actual** `Security/SitePolicy.cs` from the WPF client.
- Builds on any OS supporting .NET 10 SDK; no CEF, NuGet download, microphone or Character.AI login needed.
- Covers ordinary HTTPS links, origin spoofing, Google/Apple/Auth0 identity pages, encoded and nested OAuth keys, fragments, callbacks, and signed URLs.
- Must run successfully on a real .NET 10 installation before claiming the handoff policy compiled. It has **not** run in the source-only container (no `dotnet` executable).
- No CI, schedules, background tasks, services or recurring work.

In v0.5.0 the no-NuGet project also links the production `Pcm16Resampler.cs` and checks five deterministic audio cases. It still requires .NET 10 and has not been executed in this environment.
