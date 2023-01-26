s = ""
for i in range(100):
    s += r"\0"
print(f"public static final ONE_HUNDRED_LEN_STRING = \"{s}\";")