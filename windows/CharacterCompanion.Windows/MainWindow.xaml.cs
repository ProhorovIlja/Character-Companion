using System.Diagnostics;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Navigation;
using System.Windows.Input;
using CharacterCompanion.Windows.Security;
using CharacterCompanion.Windows.Speech;
using Microsoft.Win32;
using CefSharp;
using CharacterCompanion.Windows.Browser;

namespace CharacterCompanion.Windows;

public partial class MainWindow : Window
{
    private readonly AppSettings _settings;
    private MicrophoneRecorder? _recorder;
    private bool _busy;
    private bool _webReady;
    private bool _bridgeBusy;
    private volatile bool _filterTrackers;
    private long _navigationEpoch;
    private long _recordingEpoch;
    private string? _recordingPage;
    private long _draftRevision;
    private long _recordingDraftRevision;

    public MainWindow()
    {
        _settings = AppSettings.Load();
        InitializeComponent();
        // Track every edit, including a change that is later undone to identical text.
        DraftText.TextChanged += (_, _) => _draftRevision++;
        _filterTrackers = _settings.FilterTrackers;
        FilterCheckbox.IsChecked = _settings.FilterTrackers;
        LanguageCombo.SelectedIndex = _settings.Language switch { "ru" => 1, "en" => 2, _ => 0 };
        Loaded += (_, _) => InitializeBrowser();
        Closed += (_, _) => { _recorder?.Dispose(); Browser.Dispose(); };
    }

    private void InitializeBrowser()
    {
        try
        {
            // CEF is initialized in App.OnStartup. Attach handlers BEFORE first navigation.
            Browser.RequestHandler = new CompanionRequestHandler(() => _filterTrackers,
                uri => Dispatcher.BeginInvoke(() => OfferExternal(uri)));
            Browser.LifeSpanHandler = new CompanionPopupHandler(uri => Dispatcher.BeginInvoke(() =>
            {
                if (SitePolicy.IsFirstParty(uri)) Browser.Load(uri);
                else if (SitePolicy.IsExternalHttps(uri)) OfferExternal(uri);
                else SetStatus("Заблокировано небезопасное всплывающее окно.");
            }));
            Browser.AddressChanged += (_, e) =>
            {
                System.Threading.Interlocked.Increment(ref _navigationEpoch);
                Dispatcher.BeginInvoke(() =>
                {
                    LocationLabel.Text = SitePolicy.IsFirstParty(e.NewValue as string)
                        ? "character.ai · локальный профиль CEF" : "Ожидание Character.AI";
                });
            };
            Browser.LoadError += (_, e) =>
            {
                if (!e.Frame.IsMain || e.ErrorCode == CefErrorCode.Aborted) return;
                Dispatcher.BeginInvoke(() => SetStatus("Ошибка загрузки: " + e.ErrorCode));
            };
            _webReady = true;
            Browser.Load(SitePolicy.Home);
        }
        catch (Exception ex)
        {
            _webReady = false;
            SetStatus("CEF не запущен: " + ex.Message);
        }
    }

    private void OfferExternal(string address)
    {
        var target = SitePolicy.SafeBrowserHandoff(address);
        if (target is null)
        {
            SetStatus("Небезопасная внешняя ссылка отклонена.");
            return;
        }
        var redacted = !string.Equals(target, address, StringComparison.Ordinal);
        var message = redacted
            ? "Ссылка содержит данные авторизации или подозрительные параметры. В системном браузере откроется только главная Character.AI. Сессия CEF не перенесётся. Продолжить?"
            : "Открыть HTTPS-сайт в системном браузере?\n" + new Uri(target).Host;
        if (MessageBox.Show(this, message, "Внешний браузер", MessageBoxButton.YesNo,
                MessageBoxImage.Question) != MessageBoxResult.Yes) return;
        OpenExternal(target);
        SetStatus(redacted ? "Секреты ссылки не переданы. Сессии браузеров независимы."
            : "Открыт внешний сайт после подтверждения.");
    }

    private static void OpenExternal(string address)
    {
        // Final validation also covers direct UI actions, not only OfferExternal.
        var safe = SitePolicy.SafeBrowserHandoff(address);
        if (safe is null) return;
        try { Process.Start(new ProcessStartInfo(safe) { UseShellExecute = true }); }
        catch (Exception) { /* The system may have no default HTTPS handler. */ }
    }

    private void Back_Click(object sender, RoutedEventArgs e) { if (_webReady && Browser.CanGoBack) Browser.Back(); }
    private void Forward_Click(object sender, RoutedEventArgs e) { if (_webReady && Browser.CanGoForward) Browser.Forward(); }
    private void Reload_Click(object sender, RoutedEventArgs e) { if (_webReady) Browser.Reload(); }
    private void Home_Click(object sender, RoutedEventArgs e) { if (_webReady) Browser.Load(SitePolicy.Home); }
    private void ExternalBrowser_Click(object sender, RoutedEventArgs e) => OpenExternal(SitePolicy.Home);
    private void Filter_Checked(object sender, RoutedEventArgs e)
    {
        if (!IsLoaded) return;
        _settings.FilterTrackers = FilterCheckbox.IsChecked == true;
        _filterTrackers = _settings.FilterTrackers;
        SaveSettings();
    }

    private void Language_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (!IsLoaded) return;
        _settings.Language = (LanguageCombo.SelectedItem as ComboBoxItem)?.Tag as string ?? "auto";
        SaveSettings();
    }

    private void ChooseWhisper_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFileDialog { Filter = "whisper-cli.exe|whisper-cli.exe", Title = "Укажи whisper-cli.exe" };
        if (dialog.ShowDialog(this) == true)
        {
            _settings.WhisperExecutable = dialog.FileName;
            SaveSettings(); SetStatus("whisper.cpp выбран локально.");
        }
    }

    private void ChooseModel_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFileDialog { Filter = "GGML model (*.bin)|*.bin", Title = "Выбери локальную модель Whisper" };
        if (dialog.ShowDialog(this) == true)
        {
            _settings.WhisperModel = dialog.FileName;
            SaveSettings(); SetStatus("Модель распознавания выбрана локально.");
        }
    }

    private void SaveSettings()
    {
        try { _settings.Save(); }
        catch (IOException) { SetStatus("Не удалось сохранить настройки."); }
        catch (UnauthorizedAccessException) { SetStatus("Нет доступа к файлу настроек."); }
    }

    private void StartRecord_Click(object sender, RoutedEventArgs e)
    {
        if (_busy || _recorder is not null) return;
        if (!File.Exists(_settings.WhisperExecutable) || !File.Exists(_settings.WhisperModel))
        {
            SetStatus("Сначала выбери whisper-cli.exe и GGML-модель (.bin). Запись не начата.");
            return;
        }
        try
        {
            _recorder = new MicrophoneRecorder();
            _recorder.Start();
            // Remember the destination at the moment recording begins. The user may
            // navigate to another character/conversation while Whisper is running.
            _recordingPage = Browser.IsBrowserInitialized && SitePolicy.IsFirstParty(Browser.Address)
                ? Browser.Address : null;
            _recordingEpoch = System.Threading.Interlocked.Read(ref _navigationEpoch);
            _recordingDraftRevision = _draftRevision;
            RecordingStatus.Text = "Микрофон записывает · максимум 3 минуты";
            SetRecordingButtons(true);
        }
        catch (Exception ex)
        {
            _recorder?.Dispose(); _recorder = null;
            RecordingStatus.Text = "Микрофон выключен";
            SetStatus("Не удалось открыть микрофон: " + ex.Message);
        }
    }

    private void SetRecordingButtons(bool active)
    {
        StartRecordButton.IsEnabled = !active && !_busy;
        StopRecordButton.IsEnabled = active && !_busy;
        RecordAndSendButton.IsEnabled = active && !_busy;
        CancelRecordButton.IsEnabled = active && !_busy;
    }

    private async void StopRecord_Click(object sender, RoutedEventArgs e) => await StopAndTranscribeAsync(false);
    private async void RecordAndSend_Click(object sender, RoutedEventArgs e) => await StopAndTranscribeAsync(true);
    private async void CancelRecord_Click(object sender, RoutedEventArgs e)
    {
        if (_recorder is null || _busy) return;
        var recorder = _recorder;
        _busy = true;
        SetRecordingButtons(false);
        try { await recorder.CancelAsync(); }
        catch (Exception) { /* Treat cancellation as no transcript. */ }
        finally
        {
            MicrophoneRecorder.EraseTemporaryRecording(recorder.FilePath);
            recorder.Dispose(); _recorder = null; _recordingPage = null; _busy = false;
            SetRecordingButtons(false); RecordingStatus.Text = "Микрофон выключен";
            SetStatus("Запись отменена, временный WAV удалён.");
        }
    }

    private async Task StopAndTranscribeAsync(bool sendAfter)
    {
        if (_recorder is null || _busy) return;
        var recorder = _recorder;
        // Never auto-send an earlier edited draft or text changed while Whisper is working.
        var draftBeforeTranscription = DraftText.Text;
        var originalPage = _recordingPage;
        var originalEpoch = _recordingEpoch;
        var draftEpochAtStart = _recordingDraftRevision;
        var draftEpochAtStop = _draftRevision;
        _busy = true;
        SetRecordingButtons(false);
        string? wav = null;
        try
        {
            wav = await recorder.StopAsync();
            RecordingStatus.Text = "Локальная расшифровка…";
            var transcript = await WhisperCliTranscriber.TranscribeAsync(
                _settings.WhisperExecutable, _settings.WhisperModel, wav, _settings.Language);
            var draftEditedWhileWorking = !string.Equals(
                DraftText.Text, draftBeforeTranscription, StringComparison.Ordinal)
                || _draftRevision != draftEpochAtStop || draftEpochAtStop != draftEpochAtStart;
            var originalConversationStillOpen = originalPage is not null
                && SitePolicy.IsFirstParty(originalPage)
                && string.Equals(Browser.Address, originalPage, StringComparison.Ordinal)
                && System.Threading.Interlocked.Read(ref _navigationEpoch) == originalEpoch;
            var canAutoSend = sendAfter && string.IsNullOrWhiteSpace(draftBeforeTranscription)
                && !draftEditedWhileWorking && originalConversationStillOpen;
            DraftText.Text = string.IsNullOrWhiteSpace(DraftText.Text)
                ? transcript : DraftText.Text.TrimEnd() + " " + transcript;
            RecordingStatus.Text = "Микрофон выключен";
            if (canAutoSend)
            {
                SetStatus("Расшифровано локально. Выполняется отправка по запросу.");
                await InsertDraftAsync(true, fromTranscription: true, expectedPage: originalPage);
            }
            else if (sendAfter)
            {
                SetStatus("Диктовка добавлена в черновик. Автоотправка отменена: прежний текст, правки или смена страницы. Проверь чат и отправь вручную.");
            }
            else SetStatus("Речь расшифрована локально. Текст можно отредактировать.");
        }
        catch (Exception ex)
        {
            RecordingStatus.Text = "Микрофон выключен";
            SetStatus("Расшифровка не выполнена: " + ex.Message);
        }
        finally
        {
            MicrophoneRecorder.EraseTemporaryRecording(wav ?? recorder.FilePath);
            recorder.Dispose(); _recorder = null; _recordingPage = null; _busy = false;
            SetRecordingButtons(false);
        }
    }

    private async void InsertDraft_Click(object sender, RoutedEventArgs e) => await InsertDraftAsync(false);
    private async void SendDraft_Click(object sender, RoutedEventArgs e) => await InsertDraftAsync(true);
    private async void DraftText_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter && (Keyboard.Modifiers & ModifierKeys.Control) == ModifierKeys.Control)
        {
            e.Handled = true;
            await InsertDraftAsync(true);
        }
    }
    private void ClearDraft_Click(object sender, RoutedEventArgs e)
    {
        if (_busy || _bridgeBusy) { SetStatus("Черновик занят. Дождись завершения текущего действия."); return; }
        DraftText.Clear();
        SetStatus("Черновик очищен по запросу.");
    }

    private async Task InsertDraftAsync(bool send, bool fromTranscription = false, string? expectedPage = null)
    {
        if (_bridgeBusy || (_busy && !fromTranscription))
        {
            SetStatus("Вставка/отправка уже выполняется или идёт распознавание речи.");
            return;
        }
        var text = DraftText.Text;
        if (string.IsNullOrWhiteSpace(text)) { SetStatus("Черновик пуст."); return; }
        if (!_webReady || !Browser.IsBrowserInitialized || !SitePolicy.IsFirstParty(Browser.Address))
        {
            SetStatus("Вставка разрешена только на странице character.ai в приложении."); return;
        }
        // No silent retargeting from the originally recorded conversation to another one.
        var browserPage = Browser.Address;
        if (expectedPage is not null && !string.Equals(browserPage, expectedPage, StringComparison.Ordinal))
        {
            SetStatus("Страница переписки изменилась. Автоотправка отменена; текст сохранён в черновике.");
            return;
        }
        _bridgeBusy = true;
        try
        {
            // CEF returns JS objects through dynamic value conversion. JSON.stringify gives
            // the same strict JSON envelope used by our existing tested ComposerBridge.
            var expression = ComposerBridge.BuildScript(text, send, expectedPage ?? browserPage).Trim().TrimEnd(';');
            var script = "JSON.stringify(" + expression + ")";
            var response = await Browser.EvaluateScriptAsync(script, TimeSpan.FromSeconds(10));
            if (!response.Success || response.Result is not string json)
                throw new InvalidOperationException("CEF не выполнил DOM-вставку: " + response.Message);
            var result = ComposerBridge.ParseResult(json);
            if (!result.Inserted) SetStatus("Не удалось найти единственное поле ввода. Скопируй текст вручную. " + result.Reason);
            else if (!send) SetStatus("Черновик вставлен в поле Character.AI, отправка не выполнялась.");
            else if (result.ClickAttempted) SetStatus("Кнопка отправки нажата. Подтверди доставку сообщения в интерфейсе Character.AI.");
            else SetStatus("Текст вставлен, но кнопку отправки нельзя определить однозначно. Отправь вручную.");
        }
        catch (Exception ex) { SetStatus("Вставка в страницу не выполнена: " + ex.Message); }
        finally { _bridgeBusy = false; }
    }

    private void CopyDraft_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(DraftText.Text)) { SetStatus("Черновик пуст."); return; }
        try { Clipboard.SetText(DraftText.Text); SetStatus("Черновик скопирован по запросу."); }
        catch (Exception) { SetStatus("Буфер обмена временно недоступен."); }
    }

    private void SaveDraft_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(DraftText.Text)) { SetStatus("Черновик пуст."); return; }
        var dialog = new SaveFileDialog { Filter = "Text (*.txt)|*.txt", FileName = "character-companion-dictation.txt" };
        if (dialog.ShowDialog(this) != true) return;
        try { File.WriteAllText(dialog.FileName, DraftText.Text); SetStatus("Черновик сохранён по запросу пользователя."); }
        catch (IOException ex) { SetStatus("Не удалось сохранить: " + ex.Message); }
        catch (UnauthorizedAccessException) { SetStatus("Нет доступа к выбранному файлу."); }
    }

    private void SetStatus(string value) => StatusLabel.Text = value;
}
