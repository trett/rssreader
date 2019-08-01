import Vue from "vue";
import Component from "vue-class-component";
import {Watch} from "vue-property-decorator";
import AlertComponent from "../components/alertComponent";
import EventBus from "../eventBus";
import {IChannel, NetworkService} from "../services/networkService";

@Component({
    components: {
        alert: AlertComponent,
    },
    template: `
<v-app>
    <v-overlay :value="loading">
      <v-progress-circular indeterminate size="64"></v-progress-circular>
    </v-overlay>
    <v-navigation-drawer v-model="drawer" app dark width="300">
        <v-list dense nav sub-header>
            <v-list-item to="/settings" exact>
                <v-list-item-action>
                    <v-icon small>fa-edit</v-icon>
                </v-list-item-action>
                <v-list-item-content>
                    <v-list-item-title>Settings</v-list-item-title>
                </v-list-item-content>
            </v-list-item>
            <v-list-item key="all" :to="{path: '/channel/all', query: {t: + new Date()}}">
                <v-list-item-action>
                    <v-icon small>fa-rss</v-icon>
                </v-list-item-action>
                <v-list-item-content>
                    <v-list-item-title>All channels</v-list-item-title>
                </v-list-item-content>
            </v-list-item>
                <v-divider></v-divider>
                <v-subheader>Channels</v-subheader>
            <v-list-item v-for="channel in channels" :key="channel.title"
                        :to="{path: '/channel/' + channel.id, query: {t: + new Date()}}">
                <v-list-item-action>
                    <v-icon small>fa-rss</v-icon>
                </v-list-item-action>
                <v-list-item-content>
                    <v-list-item-title v-text="channel.title"></v-list-item-title>
                </v-list-item-content>
                <v-list-item-action>
                    <v-btn small icon @click="deleteChannel(channel)" height="24">
                        <v-icon color="grey lighten-1">delete</v-icon>
                    </v-btn>
                </v-list-item-action>
            </v-list-item>
        </v-list>
    </v-navigation-drawer>
    <v-app-bar app dense>
        <v-app-bar-nav-icon @click.stop="drawer = !drawer"></v-app-bar-nav-icon>
        <v-btn icon @click="refresh()">
              <v-icon>cached</v-icon>
        </v-btn>
        <v-dialog v-model="dialog" persistent max-width="500">
            <template v-slot:activator="{ on }">
                <v-btn icon v-on="on">
                    <v-icon dark>add</v-icon>
                </v-btn>
            </template>
            <v-card>
                <v-container>
                    <v-card-title class="headline">Add feed</v-card-title>
                        <v-text-field v-model="newChannel" label="URL" single-line autofocus
                            style="margin: 0 5px 0 5px">
                        </v-text-field>
                    <v-card-actions>
                        <v-spacer></v-spacer>
                        <v-btn color="green darken-1" text @click="dialog = false;">Cancel</v-btn>
                        <v-btn color="green darken-1" text @click="dialog = false; addChannel()">Add</v-btn>
                    </v-card-actions>
                </v-container>
            </v-card>
        </v-dialog>
        <v-spacer></v-spacer>
        <v-btn icon @click="markAllAsRead()">
            <v-icon>check</v-icon>
        </v-btn>
    </v-app-bar>
    <v-content>
        <v-container fluid>
            <alert></alert>
            <confirm ref="confirm"></confirm>
            <router-view></router-view>
        </v-container>
    </v-content>
</v-app>
`,
})
export default class Main extends Vue {

    private channels: IChannel[] = [];

    private newChannel = "";

    private loading = false;

    private dialog = false;

    private drawer = null;

    public async beforeMount(): Promise<void> {
        try {
            this.channels = await NetworkService.getChannels();
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
    }

    public mounted(): void {
        EventBus.$on("loading", () => this.loading = true);
        EventBus.$on("loadOff", () => this.loading = false);
    }

    private async refresh(): Promise<void> {
        try {
            await NetworkService.updateChannels();
            return this.update();
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
    }

    private async addChannel(): Promise<void> {
        try {
            const channelId = await NetworkService.addChannel(this.newChannel);
            await this.update();
            this.$router.push({path: `/channel/${channelId}`});
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
    }

    private async markAllAsRead(): Promise<void> {
        EventBus.$emit("markAllAsRead");
    }

    private async deleteChannel(channel: IChannel): Promise<void> {
        if (!confirm(`Do you really want to delete ${channel.title}?`)) {
            return;
        }
        try {
            await NetworkService.deleteChannel(channel.id);
            this.$router.push({path: "/"});
            await this.update();
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
    }

    private async update(): Promise<void> {
        try {
            this.channels = await NetworkService.getChannels();
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
        EventBus.$emit("updateFeeds");
    }

    @Watch("dialog")
    private onDialogVisibleChange(val: any, oldVal: any): void {
        if (val) {
            this.newChannel = "";
        }
    }
}
