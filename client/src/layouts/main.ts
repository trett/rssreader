import Vue from "vue";
import Component from "vue-class-component";
import { Watch } from "vue-property-decorator";
import AlertComponent from "../components/alertComponent";
import EventBus from "../eventBus";
import { FeedItem, IChannel, NetworkService } from "../services/networkService";

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
                    <v-icon small>fas fa-edit</v-icon>
                </v-list-item-action>
                <v-list-item-content>
                    <v-list-item-title>Settings</v-list-item-title>
                </v-list-item-content>
            </v-list-item>
            <v-list-item key="all"
                        @click="setFeeds()"
                        :to="{path: '/', query: {t: + new Date()}}">
                <v-list-item-action>
                    <v-icon small>fas fa-rss</v-icon>
                </v-list-item-action>
                <v-list-item-content>
                    <v-list-item-title>All channels</v-list-item-title>
                </v-list-item-content>
            </v-list-item>
                <v-divider></v-divider>
                <v-subheader>Channels</v-subheader>
            <v-list-item v-for="channel in channels" :key="channel.title"
                        @click="setFeeds(channel.id)"
                        :to="{path: '/', query: {t: + new Date()}}">
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
    <v-main>
        <v-container fluid>
            <alert></alert>
            <confirm ref="confirm"></confirm>
            <router-view :data="data"/>
        </v-container>
    </v-main>
</v-app>
`,
})
export default class Main extends Vue {

    private channels: IChannel[] = [];

    private newChannel = "";

    private loading = false;

    private dialog = false;

    private drawer = null;

    private selectedChannel = "";

    private data: FeedItem[] = [];

    public async beforeMount(): Promise<void> {
        this.update();
    }

    public mounted(): void {
        EventBus.$on("loading", () => this.loading = true);
        EventBus.$on("loadOff", () => this.loading = false);
        NetworkService.getAllFeeds().then(response => this.data = response);
    }

    private async setFeeds(id?: string) {
        if (id) {
            this.data = await NetworkService.getFeedsByChannelId(id);
            this.selectedChannel = id;
        } else {
            this.data = await NetworkService.getAllFeeds();
        }
    }

    private async refresh(): Promise<void> {
        await NetworkService.updateChannels();
        this.update();
        this.data = this.selectedChannel ?
            await NetworkService.getFeedsByChannelId(this.selectedChannel) : await NetworkService.getAllFeeds();
    }

    private async addChannel(): Promise<void> {
        await NetworkService.addChannel(this.newChannel);
        this.update();
    }

    private async markAllAsRead(): Promise<void> {
        NetworkService.markRead(this.data.map(feedItem => feedItem.id));
    }

    private async deleteChannel(channel: IChannel): Promise<void> {
        if (!confirm(`Do you really want to delete ${channel.title}?`)) {
            return;
        }
        NetworkService.deleteChannel(channel.id);
    }

    private async update(): Promise<void> {
        this.channels = await NetworkService.getChannels();
    }

    @Watch("dialog")
    private onDialogVisibleChange(val: any, oldVal: any): void {
        if (val) {
            this.newChannel = "";
        }
    }
}
