import Vue from "vue";
import Component from "vue-class-component";
import {Watch} from "vue-property-decorator";
import EventBus from "../eventBus";
import {ISettings, NetworkService} from "../services/networkService";
import SettingsService from "../services/settingsService";

@Component({
    template: `
        <v-layout wrap>
            <v-flex xs12>
                <v-checkbox v-model="hideRead" label="Show only unread" :true-value="true" mask="##"></v-checkbox>
            </v-flex>
            <v-flex xs2>
                <v-text-field v-model="deleteAfter" mask="##" placeholder="7" label="Days to keep feeds"></v-text-field>
             </v-flex>
             <v-flex xs3>
                <v-btn @click="deleteOldItems()">Clean feeds</v-btn>
            </v-flex>
            <v-flex xs12>
                <v-btn color="primary" @click="saveSettings()">Save</v-btn>
            </v-flex>
        </v-layout>
    `,
})
export default class SettingsComponent extends Vue {

    private hideRead = false;

    private deleteAfter = 7;

    private settings: ISettings;

    private settingsService: SettingsService;

    public async beforeMount(): Promise<void> {
        this.settingsService = new SettingsService();
        try {
            this.settings = await this.settingsService.getSettings();
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
        this.hideRead = this.settings.hideRead;
        this.deleteAfter = this.settings.deleteAfter;
    }

    @Watch("hideRead")
    private onHideReadChanged(oldVal: boolean, newVal: boolean): void {
        if (oldVal !== newVal) {
            this.settings.hideRead = this.hideRead.toString() === "true";
        }
    }

    private async saveSettings(): Promise<void> {
        this.settings.deleteAfter = this.deleteAfter;
        return this.settingsService.setSettings(JSON.stringify(this.settings));
    }

    private async deleteOldItems(): Promise<void> {
        return NetworkService.deleteOldItems();
    }
}
