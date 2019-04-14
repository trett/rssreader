import Vue from "vue";
import Component from "vue-class-component";
import {Watch} from "vue-property-decorator";
import {NetworkService, Settings} from "../services/networkService";

@Component({
    template: `
        <v-checkbox v-model="hideRead" label="Show only unread" :true-value="true"></v-checkbox>
    `
})
export default class SettingsComponent extends Vue {

    private hideRead = false;

    private settings: Settings;

    async beforeMount(): Promise<void> {
        try {
            this.settings = await NetworkService.getSettings();
        } catch (e) {
            Vue.prototype.$setError(e.message);
        }
        this.hideRead = this.settings.hideRead;
    }

    @Watch("hideRead")
    private async onHideReadChanged(oldVal: boolean, newVal: boolean): Promise<void> {
        if (oldVal !== newVal) {
            this.settings.hideRead = this.hideRead.toString() === "true";
            return NetworkService.saveSettings(JSON.stringify(this.settings))
        }
    }
}
