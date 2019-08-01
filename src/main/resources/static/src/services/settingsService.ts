import {ISettings, NetworkService} from "./networkService";

export default class SettingsService {

    public async setSettings(settings: string): Promise<void> {
        localStorage.setItem("settings", settings);
        return NetworkService.saveSettings(settings);
    }

    public async getSettings(): Promise<ISettings> {
        let settings = localStorage.getItem("settings");
        if (!settings) {
            settings = await NetworkService.getSettings();
            localStorage.setItem("settings", settings);
        }
        return JSON.parse(settings);
    }

}
