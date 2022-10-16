#
# Plot the reward and abs(delta) of tdagent
#

clear all;
PATH = "../data/agent-30e-3";
V0 = tensor_read([PATH "/v0"]);

N = size(V0)(1);

MODE = {"mean", "min", "max", "lin", "exp"};

LEN = max(round(N / 100), 1);
STRIDE = max((LEN / 2), 1);

subplot(111);
plot_trend(V0, LEN, STRIDE, MODE);
title("V0");
