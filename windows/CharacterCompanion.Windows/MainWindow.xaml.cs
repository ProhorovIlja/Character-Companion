using System.Diagnostics;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Navigation;
using CharacterCompanion.Windows.Security;
using CharacterCompanion.Windows.Speech;
using Microsoft.Win32;
using Microsoft.Web.WebView2.Core;

namespace CharacterCompanion.Windows;

public partial class MainWindow : Window
{
    private readonly AppSettings _settings;
    private MicrophoneRecorder? _recorder;
    private bool _busy;
    private bool _webReady;

    public MainWindow()
    {
        _settings = AppSettings.Load();
        InitializeComponent();
        FilterCheckbox.IsChecked = _settings.FilterTrackers;
        LanguageCombo.SelectedIndex = _settings.Language switch { "ru" => 1, "en" => 2, _ => 0 };
        Loaded += async (_, _) => await InitializeBrowserAsync();
        Closed += (_, _) => _recorder?.Dispose();
    }

    private async Task InitializeBrowserAsync()
    {
        try
        {
            var profile = Path.Combine(AppSettings.UserFolder, "WebView2");
            Directory.CreateDirectory(profile);
            var environment = await CoreWebView2Environment.CreateAsync(userDataFolder: profile);
            await Browser.EnsureCoreWebView2Async(environment);
            var core = Browser.CoreWebView2;
            core.Settings.AreDevToolsEnabled = false;
            core.Settings.IsStatusBarEnabled = false;
            core.Settings.AreDefaultContextMenusEnabled = true;
            core.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All);
            core.WebResourceRequested += (_, e) =>
            {
                if (TrackerPolicy.Block(e.Request.Uri, _settings.FilterTrackers))
                    e.Response = core.Environment.CreateWebResourceResponse(null, 403, "Filtered", "");
            };
            core.NavigationStarting += (_, e) =>
            {
                if (SitePolicy.IsFirstParty(e.Uri)) return;
                e.Cancel = true;
                if (SitePolicy.IsExternalHttps(e.Uri))
                {
                    OfferExternal(e.Uri);
                }
                else SetStatus("Заблокирована навигация на недоверенную схему или адрес.");
            };
            core.NewWindowRequested += (_, e) =>
            {
                e.Handled = true;
                if (SitePolicy.IsFirstParty(e.Uri)) core.Navigate(e.Uri);
                else if (SitePolicy.IsExternalHttps(e.Uri)) OfferExternal(e.Uri);
            };
            core.NavigationCompleted += (_, e) =>
            {
                LocationLabel.Text = SitePolicy.IsFirstParty(core.Source) ? "character.ai · защищённая сессия" : "Ожидание Character.AI";
                if (!e.IsSuccess) SetStatus("Страница не загрузилась. Проверь интернет, вход или перезагрузи.");
            };
            core.ProcessFailed += (_, _) => Dispatcher.Invoke(() => SetStatus("WebView2 остановлен. Нажми «Обновить»."));
            _webReady = true;
            core.Navigate(SitePolicy.Home);
        }
        catch (Exception ex)
        {
            SetStatus("WebView2 недоступен: " + ex.Message);
        }
    }

    private void OfferExternal(string address)
    {
        var authentication = SitePolicy.IsIdentityOrTokenUrl(address);
        var message = authentication
            ? "Для входа откроется главная страница Character.AI в системном браузере. Сессия WebView2 не перенесётся автоматически. Продолжить?"
            : "Открыть сторонний HTTPS-сайт в системном браузере?\n" + new Uri(address).Host;
        if (MessageBox.Show(this, message, "Внешний браузер", MessageBoxButton.YesNo,
                MessageBoxImage.Question) != MessageBoxResult.Yes) return;
        OpenExternal(authentication ? SitePolicy.Home : address);
        SetStatus(authentication ? "Для входа открыт сайт в системном браузере. Сессии независимы."
            : "Открыт внешний сайт после подтверждения.");
    }

    private static void OpenExternal(string address)
    {
        if (!SitePolicy.IsFirstParty(address) && !SitePolicy.IsExternalHttps(address)) return;
        try { Process.Start(new ProcessStartInfo(address) { UseShellExecute = true }); }
        catch (Exception) { /* The system may have no default HTTPS handler. */ }
    }

    private void Back_Click(object sender, RoutedEventArgs e) { if (_webReady && Browser.CanGoBack) Browser.GoBack(); }
    private void Forward_Click(object sender, RoutedEventArgs e) { if (_webReady && Browser.CanGoForward) Browser.GoForward(); }
    private void Reload_Click(object sender, RoutedEventArgs e) { if (_webReady) Browser.Reload(); }
    private void Home_Click(object sender, RoutedEventArgs e) { if (_webReady) Browser.CoreWebView2.Navigate(SitePolicy.Home); }
    private void ExternalBrowser_Click(object sender, RoutedEventArgs e) => OpenExternal(SitePolicy.Home);
    private void Filter_Checked(object sender, RoutedEventArgs e)
    {
        if (!IsLoaded) return;
        _settings.FilterTrackers = FilterCheckbox.IsChecked == true;
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
        try { await recorder.StopAsync(); }
        catch (Exception) { /* Treat cancellation as no transcript. */ }
        finally
        {
            MicrophoneRecorder.EraseTemporaryRecording(recorder.FilePath);
            recorder.Dispose(); _recorder = null; _busy = false;
            SetRecordingButtons(false); RecordingStatus.Text = "Микрофон выключен";
            SetStatus("Запись отменена, временный WAV удалён.");
        }
    }

    private async Task StopAndTranscribeAsync(bool sendAfter)
    {
        if (_recorder is null || _busy) return;
        var recorder = _recorder;
        _busy = true;
        SetRecordingButtons(false);
        string? wav = null;
        try
        {
            wav = await recorder.StopAsync();
            RecordingStatus.Text = "Локальная расшифровка…";
            var transcript = await WhisperCliTranscriber.TranscribeAsync(
                _settings.WhisperExecutable, _settings.WhisperModel, wav, _settings.Language);
            DraftText.Text = string.IsNullOrWhiteSpace(DraftText.Text) ? transcript : DraftText.Text.TrimEnd() + " " + transcript;
            RecordingStatus.Text = "Микрофон выключен";
            SetStatus("Речь расшифрована локально. Текст можно отредактировать.");
            if (sendAfter) await InsertDraftAsync(true);
        }
        catch (Exception ex)
        {
            RecordingStatus.Text = "Микрофон выключен";
            SetStatus("Расшифровка не выполнена: " + ex.Message);
        }
        finally
        {
            MicrophoneRecorder.EraseTemporaryRecording(wav ?? recorder.FilePath);
            recorder.Dispose(); _recorder = null; _busy = false;
            SetRecordingButtons(false);
        }
    }

    private async void InsertDraft_Click(object sender, RoutedEventArgs e) => await InsertDraftAsync(false);
    private async void SendDraft_Click(object sender, RoutedEventArgs e) => await InsertDraftAsync(true);

    private async Task InsertDraftAsync(bool send)
    {
        var text = DraftText.Text;
        if (string.IsNullOrWhiteSpace(text)) { SetStatus("Черновик пуст."); return; }
        if (!_webReady || !SitePolicy.IsFirstParty(Browser.CoreWebView2.Source))
        {
            SetStatus("Вставка разрешена только на странице character.ai в приложении."); return;
        }
        try
        {
            var json = await Browser.ExecuteScriptAsync(ComposerBridge.BuildScript(text, send));
            var result = ComposerBridge.ParseResult(json);
            if (!result.Inserted) SetStatus("Не удалось найти единственное поле ввода. Скопируй текст вручную. " + result.Reason);
            else if (!send) SetStatus("Черновик вставлен в поле Character.AI, отправка не выполнялась.");
            else if (result.ClickAttempted) SetStatus("Кнопка отправки нажата. Подтверди доставку сообщения в интерфейсе Character.AI.");
            else SetStatus("Текст вставлен, но кнопку отправки нельзя определить однозначно. Отправь вручную.");
        }
        catch (Exception ex) { SetStatus("Вставка в страницу не выполнена: " + ex.Message); }
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
