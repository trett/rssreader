import {ISettings, NetworkService} from "./networkService";

export default class SettingsService {

    private settings: ISettings;

    public async setSettings(settings: ISettings): Promise<void> {
        this.settings = settings;
        return NetworkService.saveSettings(this.settings);
    }

    public async getSettings(): Promise<ISettings> {
        if (!this.settings) {
            this.settings = await NetworkService.getSettings();
        }
        return this.settings;
    }
}
