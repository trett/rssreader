import Vue from "vue";
import Component from "vue-class-component";
import {Watch} from "vue-property-decorator";
import AlertComponent from "../components/alertComponent";
import EventBus from "../eventBus";
import {Channel, NetworkService} from "../services/networkService";

@Component({
    components: {
        alert: AlertComponent
    },
    template: `
<v-app>
    <v-navigation-drawer dark app>
        <v-list dense class="pt-0">
            <v-list-tile to="/settings" exact="true" exact-active-class="/settings">
                <v-list-tile-avatar>
                    <v-icon>fa-pencil-square-o</v-icon>
                </v-list-tile-avatar>
                <v-list-tile-content>Settings</v-list-tile-content>
            </v-list-tile>
            <v-list-tile to="/" exact="true" exact-active-class="/">
                <v-list-tile-avatar>
                    <v-icon>fa-rss</v-icon>
                </v-list-tile-avatar>
                <v-list-tile-content>All channels</v-list-tile-content>
            </v-list-tile>
            <v-list-tile v-for="channel in channels" :key="channel.title" :to="'/channel/' + channel.id"
                            exact="true" :exact-active-class="'/channel' + channel.id"">
                <v-list-tile-avatar>
                    <v-icon>fa-rss</v-icon>
                </v-list-tile-avatar>
                <v-list-tile-content>
                    <v-list-tile-title>{{ channel.title }}</v-list-tile-title>
                </v-list-tile-content>
                <v-list-tile-action>
                    <v-btn icon ripple @click="deleteChannel(channel)">
                        <v-icon color="grey lighten-1">fa-trash-o</v-icon>
                    </v-btn>
                </v-list-tile-action>
            </v-list-tile>
        </v-list>
    </v-navigation-drawer> 
    <v-toolbar app dense>
        <v-btn icon @click="refresh()">
              <v-icon>cached</v-icon>
        </v-btn>
        <v-dialog v-model="dialog" persistent max-width="500"> 
            <template v-slot:activator="{ on }">
                <v-btn icon color="light-green" v-on="on">
                    <v-icon dark>add</v-icon>
                </v-btn>
            </template> 
            <v-card>
                <v-container>
                    <v-card-title class="headline">Add feed</v-card-title>
                        <v-text-field v-model="newChannel" label="URL" style="margin: 0 5px 0 5px" single-line autofocus>
                        </v-text-field> 
                    <v-card-actions>
                        <v-spacer></v-spacer>
                        <v-btn color="green darken-1" flat @click="dialog = false;">Cancel</v-btn>
                        <v-btn color="green darken-1" flat @click="dialog = false; addChannel()">Add</v-btn>
                    </v-card-actions>
                </v-container>
            </v-card> 
        </v-dialog>
        <v-spacer></v-spacer>
        <v-btn icon @click="markChannelRead()">
            <v-icon>fa-chevron-down</v-icon>
        </v-btn>
    </v-toolbar>
    <v-content>
        <v-container fluid>
            <alert></alert>
            <confirm ref="confirm"></confirm>
            <router-view></router-view>
        </v-container>
    </v-content>
</v-app>
`
})
export default class Main extends Vue {

    private channels: Array<Channel> = [];

    private newChannel = "";

    private dialog = false;

    async beforeMount(): Promise<void> {
        try {
            this.channels = await NetworkService.getChannels();
            return this.refresh();
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
    };

    private async refresh(): Promise<void> {
        try {
            return NetworkService.updateChannels();
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
    }

    private async addChannel(): Promise<void> {
        try {
            const channelId = await NetworkService.addChannel(this.newChannel);
            this.channels = await NetworkService.getChannels();
            this.$router.push({path: `/channel/${channelId}`});
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
    }

    private async markChannelRead(): Promise<void> {
        const channelId = this.$route.params.id;
        try {
            if (channelId) {
                await NetworkService.markChannelAsRead(JSON.stringify([channelId]));
            } else if (confirm("Mark all channels as read?")) {
                await NetworkService.markChannelAsRead(JSON.stringify(this.channels.map(channel => channel.id)));
            }
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
        EventBus.$emit("updateFeeds");
    }

    private async deleteChannel(channel: Channel): Promise<void> {
        if (!confirm(`Do you really want to delete ${channel.title}?`)) {
            return;
        }
        try {
            await NetworkService.deleteChannel(String(channel.id));
            this.channels = await NetworkService.getChannels();
            this.$router.push({path: "/"});
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
    }

    @Watch("dialog")
    private onDialogVisibleChange(val: any, oldVal: any): void {
        if (val) {
            this.newChannel = "";
        }
    }
}
